package com.nekoplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.nekoplayer.R
import com.nekoplayer.data.model.Song
import com.nekoplayer.player.AudioPlayer
import com.nekoplayer.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * 音乐播放前台服务
 * 提供后台播放和通知栏控制
 */
class MusicPlaybackService : Service() {
    
    private val player: AudioPlayer by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var currentSong: Song? = null
    private var isPlaying = false
    
    private val binder = LocalBinder()
    
    inner class LocalBinder : Binder() {
        fun getService(): MusicPlaybackService = this@MusicPlaybackService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        
        // 监听播放状态更新通知
        serviceScope.launch {
            player.playerState.collectLatest { state ->
                isPlaying = state is PlayerState.Playing
                currentSong = (state as? PlayerState.Playing)?.song
                    ?: (state as? PlayerState.Paused)?.song
                updateNotification()
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> player.play()
            ACTION_PAUSE -> player.pause()
            ACTION_STOP -> {
                player.stop()
                stopForeground(true)
                stopSelf()
            }
            ACTION_NEXT -> { /* TODO: 下一首 */ }
            ACTION_PREV -> { /* TODO: 上一首 */ }
        }
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, buildNotification())
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    
    /**
     * 更新通知
     */
    fun updateNotification(song: Song? = currentSong) {
        currentSong = song
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }
    
    /**
     * 构建通知
     */
    private fun buildNotification(): Notification {
        val song = currentSong
        
        // 播放/暂停按钮
        val playPauseIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MusicPlaybackService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 停止按钮
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MusicPlaybackService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song?.title ?: "NekoPlayer")
            .setContentText(song?.artist ?: "未知艺术家")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (isPlaying) "暂停" else "播放",
                playPauseIntent
            )
            .addAction(android.R.drawable.ic_delete, "停止", stopIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1)
            )
            .build()
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "音乐播放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放控制"
                setSound(null, null)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    companion object {
        const val CHANNEL_ID = "neko_player_channel"
        const val NOTIFICATION_ID = 1
        
        const val ACTION_PLAY = "com.nekoplayer.ACTION_PLAY"
        const val ACTION_PAUSE = "com.nekoplayer.ACTION_PAUSE"
        const val ACTION_STOP = "com.nekoplayer.ACTION_STOP"
        const val ACTION_NEXT = "com.nekoplayer.ACTION_NEXT"
        const val ACTION_PREV = "com.nekoplayer.ACTION_PREV"
        
        /**
         * 启动服务
         */
        fun start(context: Context, song: Song? = null) {
            val intent = Intent(context, MusicPlaybackService::class.java).apply {
                putExtra("song", song?.id)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * 停止服务
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, MusicPlaybackService::class.java))
        }
    }
}
