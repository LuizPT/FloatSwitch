package me.diluir.floatswitch

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedAppsMigrationTest {
    private val first = application("Primeira", "com.example.first")
    private val second = application("Segunda", "com.example.second")

    @Test
    fun twoLegacyApplications_areMigratedInOrder() {
        assertEquals(
            listOf(first, second),
            SelectedAppsMigration.fromLegacyValues(listOf(encoded(first), encoded(second))),
        )
    }

    @Test
    fun oneLegacyApplication_isMigrated() {
        assertEquals(
            listOf(first),
            SelectedAppsMigration.fromLegacyValues(listOf(encoded(first), null)),
        )
    }

    @Test
    fun emptyLegacyConfiguration_becomesAnEmptyList() {
        assertEquals(
            emptyList<SelectedApp>(),
            SelectedAppsMigration.fromLegacyValues(listOf(null, null)),
        )
    }

    @Test
    fun malformedLegacyValues_areIgnored() {
        assertEquals(
            listOf(second),
            SelectedAppsMigration.fromLegacyValues(listOf("malformed", encoded(second))),
        )
    }

    @Test
    fun duplicatePackages_areRemovedWithoutChangingFirstOccurrenceOrder() {
        val duplicate = first.copy(
            displayName = "Primeira alternativa",
            activityName = "com.example.first.AlternativeActivity",
        )

        assertEquals(
            listOf(first, second),
            SelectedAppsMigration.fromLegacyValues(
                listOf(encoded(first), encoded(duplicate), encoded(second)),
            ),
        )
    }

    @Test
    fun existingNewSchema_isNeverOverwrittenByLegacyValues() {
        val current = application("Actual", "com.example.current")

        assertEquals(
            listOf(current),
            SelectedAppsMigration.resolve(
                newSchemaExists = true,
                newEncodedValues = listOf(encoded(current)),
                legacyEncodedValues = listOf(encoded(first), encoded(second)),
            ),
        )
    }

    private fun encoded(application: SelectedApp): String = SelectedAppCodec.encode(application)

    private fun application(name: String, packageName: String): SelectedApp = SelectedApp(
        displayName = name,
        packageName = packageName,
        activityName = "$packageName.MainActivity",
    )
}
