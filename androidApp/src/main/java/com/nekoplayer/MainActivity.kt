package com.nekoplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nekoplayer.service.PlaybackServiceManager
import com.nekoplayer.ui.App
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    
    private val playbackServiceManager: PlaybackServiceManager by inject()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            App()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 如果应用被完全关闭，停止服务
        if (isFinishing) {
            playbackServiceManager.release()
        }
    }
}
