package me.diluir.floatswitch

import java.nio.charset.StandardCharsets
import java.util.Base64

data class SelectedApp(
    val displayName: String,
    val packageName: String,
    val activityName: String,
)

enum class AppSlot(val storageKey: String) {
    FIRST("selected_app_1"),
    SECOND("selected_app_2");

    companion object {
        fun fromStorageKey(storageKey: String?): AppSlot? =
            values().firstOrNull { it.storageKey == storageKey }
    }
}

object SelectedAppCodec {
    private const val FIELD_SEPARATOR = "|"

    fun encode(selectedApp: SelectedApp): String = listOf(
        selectedApp.displayName,
        selectedApp.packageName,
        selectedApp.activityName,
    ).joinToString(FIELD_SEPARATOR, transform = ::encodeField)

    fun decode(encodedValue: String?): SelectedApp? {
        if (encodedValue.isNullOrBlank()) return null

        val fields = encodedValue.split(FIELD_SEPARATOR)
        if (fields.size != 3) return null

        return try {
            val decodedFields = fields.map(::decodeField)
            if (decodedFields.any(String::isBlank)) return null
            SelectedApp(
                displayName = decodedFields[0],
                packageName = decodedFields[1],
                activityName = decodedFields[2],
            )
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun encodeField(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeField(value: String): String = String(
        Base64.getUrlDecoder().decode(value),
        StandardCharsets.UTF_8,
    )
}

object AppSelectionRules {
    fun canUseInSlot(candidate: SelectedApp, otherSelection: SelectedApp?): Boolean =
        otherSelection == null || candidate.packageName != otherSelection.packageName
}
