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

object SelectedAppsRules {
    const val MAX_APPLICATIONS = 6

    fun sanitize(applications: List<SelectedApp>): List<SelectedApp> = applications
        .filter(::isValid)
        .distinctBy(SelectedApp::packageName)
        .take(MAX_APPLICATIONS)

    fun add(
        applications: List<SelectedApp>,
        candidate: SelectedApp,
    ): List<SelectedApp> {
        val current = sanitize(applications)
        if (
            current.size >= MAX_APPLICATIONS ||
            current.any { it.packageName == candidate.packageName } ||
            !isValid(candidate)
        ) {
            return current
        }
        return current + candidate
    }

    fun replace(
        applications: List<SelectedApp>,
        index: Int,
        candidate: SelectedApp,
    ): List<SelectedApp> {
        val current = sanitize(applications)
        if (
            index !in current.indices ||
            !isValid(candidate) ||
            current.withIndex().any { (otherIndex, app) ->
                otherIndex != index && app.packageName == candidate.packageName
            }
        ) {
            return current
        }
        return current.toMutableList().apply { this[index] = candidate }
    }

    fun removeAt(applications: List<SelectedApp>, index: Int): List<SelectedApp> {
        val current = sanitize(applications)
        if (index !in current.indices) return current
        return current.toMutableList().apply { removeAt(index) }
    }

    fun move(applications: List<SelectedApp>, fromIndex: Int, toIndex: Int): List<SelectedApp> {
        val current = sanitize(applications)
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) {
            return current
        }
        return current.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
    }

    fun availableApplications(
        applications: List<SelectedApp>,
        availability: List<Boolean>,
    ): List<SelectedApp> = sanitize(
        applications.filterIndexed { index, _ -> availability.getOrElse(index) { false } },
    )

    private fun isValid(application: SelectedApp): Boolean =
        application.displayName.isNotBlank() &&
            application.packageName.isNotBlank() &&
            application.activityName.isNotBlank()
}

object SelectedAppsMigration {
    fun fromLegacyValues(encodedValues: List<String?>): List<SelectedApp> =
        SelectedAppsRules.sanitize(encodedValues.mapNotNull(SelectedAppCodec::decode))

    fun resolve(
        newSchemaExists: Boolean,
        newEncodedValues: List<String?>,
        legacyEncodedValues: List<String?>,
    ): List<SelectedApp> = if (newSchemaExists) {
        SelectedAppsRules.sanitize(newEncodedValues.mapNotNull(SelectedAppCodec::decode))
    } else {
        fromLegacyValues(legacyEncodedValues)
    }
}
