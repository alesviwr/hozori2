package com.smartattendance.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * پخش‌کننده Audio Challenge روی اسپیکر استاد.
 * از MODE_STATIC با Loop Points بی‌نهایت استفاده می‌کند تا چالش
 * تا زمان تعویض (۱۲ ثانیه) به‌صورت پیوسته پخش شود.
 *
 * کلاس Stateless نیست ولی Thread-Safe است؛ فراخوانی play جدید
 * پخش قبلی را قطع و جایگزین می‌کند.
 */
class TonePlayer {

    private var track: AudioTrack? = null

    /** شروع پخش پیوسته توکن (loop بی‌پایان با فاصله ۴۰۰ms بین تکرارها) */
    suspend fun play(token: String) = withContext(Dispatchers.IO) {
        release()
        val pcm = buildLoopPcm(token)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(AudioChallengeCodec.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()

        track.write(pcm, 0, pcm.size)
        track.setLoopPoints(0, pcm.size, -1)
        track.setVolume(1.0f)
        track.play()
        this@TonePlayer.track = track
    }

    fun stop() {
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        release()
    }

    private fun release() {
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
    }

    /** بلوک پخش = توکن + سکوت، به‌طوری که Loop فاصله طبیعی داشته باشد */
    private fun buildLoopPcm(token: String): ShortArray {
        val encoded = AudioChallengeCodec.encode(token)
        val gap = AudioChallengeCodec.msToSamples(400L)
        return encoded + ShortArray(gap)
    }
}
