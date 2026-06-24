package com.victor.postly.utils

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Efeitos sonoros curtos de feedback (ex.: ao enviar uma mensagem ou curtir
 * um post). Usa [ToneGenerator], então não depende de arquivos de áudio.
 */
object SoundEffectHelper {

    private val handler = Handler(Looper.getMainLooper())

    /** Bipe curto de confirmação para ações como enviar mensagem ou curtir. */
    fun playTap(volume: Int = 70) {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, volume)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            // Libera o recurso depois que o tom termina
            handler.postDelayed({ toneGenerator.release() }, 200)
        } catch (_: RuntimeException) {
            // ToneGenerator pode falhar se o stream de áudio estiver indisponível
        }
    }
}
