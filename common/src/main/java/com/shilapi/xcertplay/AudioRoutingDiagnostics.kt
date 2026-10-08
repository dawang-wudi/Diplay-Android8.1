package com.shilapi.xcertplay

import android.content.Context
import android.media.AudioManager
import java.lang.reflect.Modifier

/** Read-only details of the actual ROM; do not assume another manufacturer's stream IDs. */
internal object AudioRoutingDiagnostics {
    fun report(context: Context): String = buildString {
        appendLine("Saved audio: mediaStream=${AirPlayPersistence.loadMediaAudioChannel(context)} " +
            "navigationStream=${AirPlayPersistence.loadNavigationAudioChannel(context)} " +
            "mediaFocus=${AirPlayPersistence.loadAudioFocusEnabled(context)} " +
            "navigationFocus=${AirPlayPersistence.loadNavigationAudioFocusEnabled(context)} " +
            "siriUsesNavigation=${AirPlayPersistence.loadSiriUsesNavigation(context)}")
        val manager = context.getSystemService(AudioManager::class.java)
        if (manager == null) { appendLine("AudioManager unavailable"); return@buildString }
        appendLine("AudioManager implementation=${manager.javaClass.name}")
        val constants = AudioManager::class.java.fields.mapNotNull { field ->
            if (!field.name.startsWith("STREAM_") || field.type != Int::class.javaPrimitiveType ||
                !Modifier.isStatic(field.modifiers)) return@mapNotNull null
            runCatching { field.name to field.getInt(null) }.getOrNull()
        }.sortedBy { it.first }
        constants.forEach { (name, id) -> appendLine("Audio constant $name=$id") }
        val ids = ((0..20).toList() + constants.map { it.second }).filter { it in 0..63 }.distinct().sorted()
        ids.forEach { id ->
            val state = runCatching {
                "volume=${manager.getStreamVolume(id)} max=${manager.getStreamMaxVolume(id)}"
            }.getOrElse { "unavailable=${it.javaClass.simpleName}" }
            appendLine("Audio stream $id $state")
        }
        // Device addresses and product names can identify phones: only report output types.
        val outputs = runCatching { manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.distinct() }
            .getOrDefault(emptyList())
        appendLine("Audio output types=${outputs.joinToString(",")}")
        appendLine("Stream initialization does not prove a separate vehicle volume group.")
    }
}
