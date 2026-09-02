package me.diluir.floatswitch

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectedAppsRulesTest {
    private val applications = (1..7).map(::application)

    @Test
    fun list_acceptsOneAndSixButNotASeventhApplication() {
        assertEquals(1, SelectedAppsRules.add(emptyList(), applications[0]).size)
        val six = applications.take(6).fold(emptyList<SelectedApp>()) { current, app ->
            SelectedAppsRules.add(current, app)
        }

        assertEquals(6, six.size)
        assertEquals(six, SelectedAppsRules.add(six, applications[6]))
    }

    @Test
    fun duplicatePackages_areRejectedOnAddAndReplace() {
        val firstTwo = applications.take(2)
        val duplicate = application(9).copy(packageName = firstTwo[0].packageName)

        assertEquals(firstTwo, SelectedAppsRules.add(firstTwo, duplicate))
        assertEquals(firstTwo, SelectedAppsRules.replace(firstTwo, 1, duplicate))
    }

    @Test
    fun addRemoveAndReorder_preserveExpectedOrder() {
        val firstThree = applications.take(3)
        val removed = SelectedAppsRules.removeAt(firstThree, 1)
        val moved = SelectedAppsRules.move(removed, 1, 0)

        assertEquals(listOf(applications[0], applications[2]), removed)
        assertEquals(listOf(applications[2], applications[0]), moved)
    }

    @Test
    fun oneUnavailableApplication_doesNotBlockTheValidOne() {
        assertEquals(
            listOf(applications[1]),
            SelectedAppsRules.availableApplications(applications.take(2), listOf(false, true)),
        )
    }

    @Test
    fun allUnavailableApplications_produceAnEmptyList() {
        assertEquals(
            emptyList<SelectedApp>(),
            SelectedAppsRules.availableApplications(applications.take(2), listOf(false, false)),
        )
    }

    private fun application(number: Int): SelectedApp = SelectedApp(
        displayName = "Aplicação $number",
        packageName = "com.example.app$number",
        activityName = "com.example.app$number.MainActivity",
    )
}
