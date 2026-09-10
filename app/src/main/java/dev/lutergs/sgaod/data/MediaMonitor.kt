package dev.lutergs.sgaod.data

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.*
import android.os.Handler
import dev.lutergs.sgaod.domain.MusicState
import dev.lutergs.sgaod.service.AODNotificationListener

/** Metadata callbacks only while content is visible. No artwork decoding or playback-position timer. */
class MediaMonitor(context: Context, private val handler: Handler, private val changed: (MusicState) -> Unit) {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val component = ComponentName(context, AODNotificationListener::class.java)
    private val callbacks = linkedMapOf<MediaController, MediaController.Callback>()
    private var running = false
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { sessions -> bind(sessions.orEmpty()) }

    fun start() {
        if (running) return
        try {
            manager.addOnActiveSessionsChangedListener(listener, component, handler)
            running = true
            bind(manager.getActiveSessions(component))
        } catch (_: SecurityException) { stop() }
    }
    fun stop() {
        if (running) manager.removeOnActiveSessionsChangedListener(listener)
        running = false
        callbacks.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        callbacks.clear()
        changed(MusicState())
    }
    private fun bind(sessions: List<MediaController>) {
        callbacks.forEach { (controller, callback) -> controller.unregisterCallback(callback) }
        callbacks.clear()
        sessions.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
                override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
                override fun onSessionDestroyed() {
                    callbacks.remove(controller)?.let { controller.unregisterCallback(it) }
                    publish()
                }
            }
            callbacks[controller] = callback
            controller.registerCallback(callback, handler)
        }
        publish()
    }
    private fun publish() {
        val controller = callbacks.keys.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: callbacks.keys.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
        val metadata = controller?.metadata
        changed(MusicState(
            title = (metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)).orEmpty().take(160),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty().take(160),
            playing = controller?.playbackState?.state == PlaybackState.STATE_PLAYING,
        ))
    }
}
