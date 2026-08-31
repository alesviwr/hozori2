package com.smartattendance.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * پخش‌کننده Audio Challenge روی اسپیکر استاد.
 * از MODE_STATIC با Loop Points بی‌نهایت استفاده می‌کند تا چالش
 * به‌صورت پیوسته پخش شود (تا زمانی که stop() یا play() جدید صدا زده شود).
 *
 * کلاس Stateless نیست ولی Thread-Safe است؛ فراخوانی play جدید
 * پخش قبلی را قطع و جایگزین می‌کند.
 */
class TonePlayer(private val context: Context) {

    private var track: AudioTrack? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var previousVolume: Int? = null
    private var focusRequest: AudioFocusRequest? = null

    /** شروع پخش پیوسته توکن (loop بی‌پایان با فاصله ۴۰۰ms بین تکرارها) */
    suspend fun play(token: String) = withContext(Dispatchers.IO) {
        release()

        // ولوم استریم Media گوشی را موقتاً بیشینه می‌کنیم — AudioTrack.setVolume فقط
        // gain داخلی است و ولوم واقعی دستگاه (که کاربر تنظیم کرده) را عوض نمی‌کند.
        // اگر ولوم media استاد پایین باشد، توکن صوتی خیلی ضعیف پخش می‌شود و میکروفون
        // دانشجوها به‌سختی/دیر آن را می‌گیرند — همان چیزی که «باید چندبار بشنود» را توضیح می‌دهد.
        if (previousVolume == null) {
            previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
        runCatching {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
        }

        requestAudioFocus()

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
        restoreVolume()
        abandonAudioFocus()
    }

    private fun requestAudioFocus() {
        runCatching {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
            audioManager.requestAudioFocus(request)
            focusRequest = request
        }
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
    }

    private fun restoreVolume() {
        previousVolume?.let { vol ->
            runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0) }
        }
        previousVolume = null
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
