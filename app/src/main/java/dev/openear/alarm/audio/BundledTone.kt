// SPDX-License-Identifier: GPL-3.0-or-later
package dev.openear.alarm.audio

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

/** An offline synthesized looping fallback; no network, proprietary sound, or binary asset. */
internal object BundledTone {
    fun getOrCreate(context: Context): File {
        val folder = context.getDir("tone", Context.MODE_PRIVATE)
        val destination = File(folder, "alarm-v1.wav")
        if (destination.length() > 44L) return destination
        val rate = 16_000
        val seconds = 2
        val samples = rate * seconds
        val size = samples * 2
        val temporary = File(folder, "alarm-v1.tmp")
        FileOutputStream(temporary).use { output ->
            fun write16(value: Int) {
                output.write(value and 255)
                output.write(value ushr 8 and 255)
            }
            fun write32(value: Int) {
                write16(value and 65535)
                write16(value ushr 16)
            }
            output.write("RIFF".toByteArray(Charsets.US_ASCII))
            write32(36 + size)
            output.write("WAVEfmt ".toByteArray(Charsets.US_ASCII))
            write32(16)
            write16(1) // PCM
            write16(1) // mono
            write32(rate)
            write32(rate * 2)
            write16(2)
            write16(16)
            output.write("data".toByteArray(Charsets.US_ASCII))
            write32(size)
            repeat(samples) { i ->
                val position = i.toDouble() / rate
                val frequency = if ((position * 2).toInt() % 2 == 0) 740.0 else 880.0
                val envelope = if ((position * 2).toInt() % 2 == 0) 0.65 else 0.75
                val amplitude = (sin(2 * PI * frequency * position) * envelope * 15000).toInt()
                write16(amplitude)
            }
        }
        check(temporary.renameTo(destination)) { "Could not prepare fallback alarm sound" }
        return destination
    }
}
