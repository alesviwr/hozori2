package com.smartattendance.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.io.IOException

/**
 * دریافت زنده صدای محیط با AudioRecord.
 *
 * اصول:
 * • فقط Framing زنده — هیچ فایلی ذخیره نمی‌شود
 * • منبع VOICE_RECOGNITION برای کاهش پردازش‌های مخرب تن (AGC/NS)
 * • Permission🎙 RECORD_AUDIO باید قبل از collect گرفته شده باشد
 */
class MicRecorder {

    private var record: AudioRecord? = null

    /** جریان فریم‌های ۲۰۴۸ نمونه‌ای (~۴۶ms) از میکروفون */
    @SuppressLint("MissingPermission") // بررسی Permission در لایه UI انجام می‌شود
    fun frames(): Flow<ShortArray> = flow {
        val minBuffer = AudioRecord.getMinBufferSize(
            AudioChallengeCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) throw IOException("AudioRecord not supported")

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            AudioChallengeCodec.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer * 2, 16_384),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IOException("microphone unavailable")
        }
        record = recorder
        recorder.startRecording()

        try {
            val buffer = ShortArray(CHUNK)
            while (currentCoroutineContext().isActive) {
                val read = recorder.read(buffer, 0, CHUNK)
                if (read > 0) emit(buffer.copyOf(read))
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            record = null
        }
    }

    fun stop() {
        runCatching { record?.stop() }
    }

    private companion object {
        const val CHUNK = 2048
    }
}
