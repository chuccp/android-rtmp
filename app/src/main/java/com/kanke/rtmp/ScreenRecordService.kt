package com.kanke.rtmp

import android.app.*
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.butterflytv.rtmp_client.RTMPMuxer
import java.util.*


class ScreenRecordService : Service() {
    override fun onCreate() {
        this.mediaProjectionManager =
            this.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    override fun onBind(p0: Intent?): IBinder? {
        return ScreenRecordBinder(this)
    }

    lateinit var mediaProjection: MediaProjection
    lateinit var mediaProjectionManager: MediaProjectionManager
    var mediaCodec: MediaCodec? = null
    var width: Int = 0
    var height: Int = 0
    var dpi: Int = 0

    @RequiresApi(Build.VERSION_CODES.S)
    fun initMediaCodec(): Surface? {
        val colorFormat = this.getSupportColorFormat()
        if (colorFormat != -1) {
            this.mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val mediaFormat = MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                this.width,
                this.height
            )
            Log.i("=====", "编码支持 $colorFormat")
            mediaFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                colorFormat
            )
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, this.width * this.height)
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 20)
            mediaFormat.setInteger(
                MediaFormat.KEY_I_FRAME_INTERVAL,
                2
            )
            this.mediaCodec!!.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = mediaCodec!!.createInputSurface()
            mediaCodec!!.start()
            return surface
        } else {

            Toast.makeText(this, "未找到编码器", Toast.LENGTH_LONG).show()
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent!!.hasExtra("code")) {
            notification()
            var code = intent!!.getIntExtra("code", -1)
            this.width = (intent!!.getIntExtra("width", 720))
            this.width = this.width / 2
            this.height = (intent!!.getIntExtra("height", 1080))
            this.height = this.height / 2
            this.dpi = intent!!.getIntExtra("dpi", 96)
            var data = intent.getParcelableExtra<Intent?>("data")
            Log.i(
                "!!!!!!",
                "mediaProjection 初始化 width:${this.width} height:${this.height} dpi:${dpi}"
            )
            val surface = initMediaCodec()
            if (mediaCodec != null && surface != null) {
                this.mediaProjection =
                    this.mediaProjectionManager.getMediaProjection(code, data!!).apply {
                        createVirtualDisplay(
                            "display",
                            this@ScreenRecordService.width,
                            this@ScreenRecordService.height,
                            this@ScreenRecordService.dpi,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                            surface,
                            null,
                            null
                        )
                    }
                this.startRecord()
            }

        } else {
            if (intent != null && intent!!.hasExtra("cz")) {
                var cz = intent!!.getStringExtra("cz")
                if (cz == "stop") {
                    Log.i("$$$$$$", "ScreenRecordService 停止录像")

                }
            }
            Log.i("$$$$$$", "ScreenRecordService 初始化失败")
        }
        return super.onStartCommand(intent, flags, startId)
    }


    private fun startRecord() {
        GlobalScope.launch {
            var rtmpMuxer = RTMPMuxer()
            rtmpMuxer.open(
                "rtmp://124.220.164.58:1935/live/abc",
                this@ScreenRecordService.width,
                this@ScreenRecordService.height
            )
            if (!rtmpMuxer.isConnected) {
                Toast.makeText(this@ScreenRecordService, "连接服务器失败", Toast.LENGTH_LONG).show()
            }
            var startTime: Long = 0
            Log.i("=====", "开始记录")
            while (true) {
                val mBufferInfo = MediaCodec.BufferInfo()
                val index = this@ScreenRecordService.mediaCodec!!.dequeueOutputBuffer(mBufferInfo, 20_000)
                Log.i("=========", "status :$index")
                when (index) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = this@ScreenRecordService.mediaCodec!!.outputFormat
                        Log.i("=========", "sps :${outputFormat.getByteBuffer("csd-0")}")
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {

                    }
                    else -> {


                        if (mBufferInfo.flags != MediaCodec.BUFFER_FLAG_CODEC_CONFIG && mBufferInfo.size != 0) {
                            val realData =
                                this@ScreenRecordService.mediaCodec!!.getOutputBuffer(index)
                            Log.i(
                                "=======",
                                "读取数据 ${realData!!.remaining()} 时长 ${mBufferInfo.presentationTimeUs / 1000 }"
                            )

                            if (realData!!.remaining() > 0) {
                                val byteArray = ByteArray(realData!!.remaining())
                                realData.get(byteArray, 0, realData!!.remaining())
                                Log.i("====AAA==", byteArray.toList().toString())
                                val sss = rtmpMuxer.writeVideo(
                                    byteArray,
                                    0,
                                    byteArray.size,
                                    mBufferInfo.presentationTimeUs/1000
                                )
//                                startTime = mBufferInfo.presentationTimeUs
                                Log.i("===AAAA===", "writeLen: ${sss}")
                            }

                        } else {
                            val realData =
                                this@ScreenRecordService.mediaCodec!!.getOutputBuffer(index)
                            val byteArray = ByteArray(realData!!.remaining())
                            realData.get(byteArray, 0, realData!!.remaining())
                            Log.i("===kkkk===", byteArray.toList().toString())
                            val sss = rtmpMuxer.writeVideo(byteArray, 0, byteArray.size, 0)

                            Log.i("===kkkk===", "writeLen: ${sss}")


                        }
                        this@ScreenRecordService.mediaCodec!!.releaseOutputBuffer(index, false)
                    }
                }

            }
        }

    }

    private fun getSupportColorFormat(): Int {
        val numCodecs = MediaCodecList.getCodecCount()
        var codecInfo: MediaCodecInfo? = null
        run {
            var i = 0
            while (i < numCodecs && codecInfo == null) {
                val info = MediaCodecList.getCodecInfoAt(i)
                if (!info.isEncoder) {
                    i++
                    continue
                }
                val types = info.supportedTypes
                var found = false
                var j = 0
                while (j < types.size && !found) {
                    if (types[j] == MediaFormat.MIMETYPE_VIDEO_AVC) {
                        println("found")
                        found = true
                    }
                    j++
                }
                if (!found) {
                    i++
                    continue
                }
                codecInfo = info
                i++
            }
        }
        Log.e(
            "AvcEncoder",
            "Found " + codecInfo!!.name + " supporting " + MediaFormat.MIMETYPE_VIDEO_AVC
        )

        // Find a color profile that the codec supports
        val capabilities = codecInfo!!.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
        Log.e(
            "AvcEncoder",
            "length-" + capabilities.colorFormats.size + "==" + Arrays.toString(capabilities.colorFormats)
        )
        for (i in capabilities.colorFormats.indices) {
            when (capabilities.colorFormats[i]) {
                CodecCapabilities.COLOR_FormatYUV420SemiPlanar, CodecCapabilities.COLOR_FormatYUV420Planar -> {
                    Log.e("AvcEncoder", "choice color format " + capabilities.colorFormats[i])
                    return capabilities.colorFormats[i]
                }
                else -> Log.e("AvcEncoder", "other color format " + capabilities.colorFormats[i])
            }
        }
        //return capabilities.colorFormats[i];
        return -1
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun notification() {
        var activityIntent = Intent(this, MainActivity::class.java)
        var pendingIntent =
            PendingIntent.getActivity(application, 0, activityIntent, PendingIntent.FLAG_IMMUTABLE)

        var notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        var notificationChannel =
            NotificationChannel("webRTC", "webRTC_name", NotificationManager.IMPORTANCE_LOW)
        notificationManager.createNotificationChannel(notificationChannel)
        var notify = Notification.Builder(application, "webRTC")
            .setAutoCancel(true)
            .setSmallIcon(R.drawable.ic_launcher_foreground).setTicker("服务器启动")
            .setContentTitle("后台服务").setContentText("后台服务等待运行").setWhen(System.currentTimeMillis())
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notify)
    }

    class ScreenRecordBinder(var service: ScreenRecordService) : Binder() {

    }
}