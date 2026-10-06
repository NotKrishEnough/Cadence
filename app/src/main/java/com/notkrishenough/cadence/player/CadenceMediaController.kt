package com.notkrishenough.cadence.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.notkrishenough.cadence.MainActivity
import com.google.common.util.concurrent.ListenableFuture

object CadenceMediaController {
    fun connect(context: Context): ListenableFuture<MediaController> {
        val token = SessionToken(context, ComponentName(context, CadencePlaybackService::class.java))
        return MediaController.Builder(context, token).buildAsync()
    }
}
