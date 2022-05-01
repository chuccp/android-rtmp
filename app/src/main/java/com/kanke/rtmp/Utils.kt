package com.kanke.rtmp

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class Utils {

    companion object{

        fun applyPermission(context: Activity, vararg permissions:String):Boolean{

            if( checkPermission(context,*permissions)){
                return true
            }
            Log.i("=====","权限申请")
            ActivityCompat.requestPermissions(context, permissions,1101)
            return false
        }

        fun checkPermission(context: Context, vararg permissions:String):Boolean{
            permissions.forEach {
                if (ContextCompat.checkSelfPermission(context,it)!= PackageManager.PERMISSION_GRANTED){
                    return false
                }
            }
            return true

        }

        fun getDisplay(context: Context): Display? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getDisplayApiR(context)
            } else {
                getDisplayApiL(context)
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        private fun getDisplayApiL(context: Context): Display? {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            return wm.defaultDisplay
        }

        @RequiresApi(api = Build.VERSION_CODES.R)
        private fun getDisplayApiR(context: Context): Display? {
            return context.display
        }
    }
}