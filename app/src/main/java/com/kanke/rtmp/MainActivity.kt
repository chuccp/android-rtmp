package com.kanke.rtmp

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi

class MainActivity : AppCompatActivity() {

    lateinit var startButton:Button
    lateinit var displayMetrics: DisplayMetrics
    lateinit var mediaProjectionManager: MediaProjectionManager
    lateinit var screenRecordService: ScreenRecordService
    private lateinit var activityResult: ActivityResultLauncher<Intent>

    private var permissions = arrayOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.RECORD_AUDIO,
    )
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startButton = findViewById(R.id.start)
        displayMetrics = DisplayMetrics()
        Utils.getDisplay(this)!!.getMetrics(displayMetrics)
        if (Utils.applyPermission(this, *permissions)){
            initMediaProject()
            startButton.setOnClickListener {
                if (Utils.checkPermission(this, *permissions)) {
                    val captureIntent: Intent = this.mediaProjectionManager.createScreenCaptureIntent()
                    this.activityResult.launch(captureIntent)
                } else {
                    Toast.makeText(this, "权限不够", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initMediaProject() {
        this.mediaProjectionManager =
            this.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        this.activityResult =
            this.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
                ActivityResultCallback {
                    Log.i("========", it.resultCode.toString())
                    if (it.resultCode == RESULT_OK) {
                        Toast.makeText(this, "初始化成功", Toast.LENGTH_LONG).show()
                        Intent(this, ScreenRecordService::class.java).apply {
                            putExtra("code", it.resultCode)
                            putExtra("data", it.data)
                            putExtra("height", displayMetrics.heightPixels)
                            putExtra("width", displayMetrics.widthPixels)
                            putExtra("dpi", displayMetrics.densityDpi)
                            Log.i("======", "startForegroundService")
                            this@MainActivity.startForegroundService(this)
                        }
                    } else {
                        Toast.makeText(this, "初始化失败", Toast.LENGTH_LONG).show()
                    }
                })
    }
}