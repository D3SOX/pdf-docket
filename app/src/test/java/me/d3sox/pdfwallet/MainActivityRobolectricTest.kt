package me.d3sox.pdfwallet

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import me.d3sox.pdfwallet.coroutines.dispatcher.di.CoroutineDispatchersModule
import me.d3sox.pdfwallet.db.di.DatabaseModule
import me.d3sox.pdfwallet.documents.DocumentFilter
import me.d3sox.pdfwallet.documents.DocumentRepository
import me.d3sox.pdfwallet.pdfimporter.PendingCertificate
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import javax.inject.Inject

@HiltAndroidTest
@UninstallModules(DatabaseModule::class, CoroutineDispatchersModule::class)
@RunWith(RobolectricTestRunner::class)
// hiding overlay windows needs a permission that Robolectric doesn't grant on Android 12+,
// and a phone-sized screen keeps every seeded document in view
@Config(application = HiltTestApplication::class, sdk = [30], qualifiers = "w411dp-h891dp")
class MainActivityRobolectricTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var repository: DocumentRepository
    @Inject lateinit var filter: DocumentFilter

    private fun seedDocuments() = runTest {
        hiltRule.inject()
        listOf("Ticket A", "Ticket B", "Other document").forEach { name ->
            repository.file(name).writeText("PDF placeholder of $name")
            repository.add(PendingCertificate(fileName = name, documentName = name))
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("Ticket A")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun selectTickets() {
        composeRule.onNodeWithText("Ticket A").performTouchInput { longClick() }
        composeRule.onNodeWithText("Ticket B").performClick()
        composeRule.onNodeWithText("2 selected").assertIsDisplayed()
    }

    private fun selectionAction(label: Int) {
        val context = composeRule.activity
        composeRule.onNode(
            hasContentDescription(context.getString(R.string.more_options)) and !hasAnyAncestor(hasScrollAction()),
        ).performClick()
        composeRule.onNodeWithText(context.getString(label)).performClick()
    }

    @Test
    fun longPress_selectsDocumentsAndBackClearsSelection() {
        seedDocuments()
        selectTickets()
        composeRule.onNodeWithText("Ticket A").assertIsSelected()
        composeRule.onNodeWithText("Ticket B").assertIsSelected()
        composeRule.onNodeWithText("Other document").assertIsNotSelected()

        composeRule.onNodeWithText("Ticket B").performClick()
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.import_pdf))
            .assertIsDisplayed()
    }

    @Test
    fun selectAll_respectsFilterAndDeletionOnlyRemovesSelection() = runTest {
        seedDocuments()
        composeRule.runOnIdle { filter.setQuery("Ticket") }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("Other document")).fetchSemanticsNodes().isEmpty()
        }
        selectionAction(R.string.select_all)
        composeRule.onNodeWithText("2 selected").assertIsDisplayed()
        selectionAction(R.string.delete)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.cancel)).performClick()
        composeRule.onNodeWithText("2 selected").assertIsDisplayed()
        repository.documents.first().size shouldBe 3

        selectionAction(R.string.delete)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.delete)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("Ticket A")).fetchSemanticsNodes().isEmpty()
        }
        repository.documents.first().map { it.name } shouldBe listOf("Other document")
    }

    @Test
    fun share_sendsOnlySelectedDocuments() {
        seedDocuments()
        selectTickets()

        selectionAction(R.string.share)

        val chooser = shadowOf(composeRule.activity).nextStartedActivity
        chooser.action shouldBe Intent.ACTION_CHOOSER
        val send = androidx.core.content.IntentCompat.getParcelableExtra(
            chooser, Intent.EXTRA_INTENT, Intent::class.java,
        )!!
        send.action shouldBe Intent.ACTION_SEND_MULTIPLE
        val uris = androidx.core.content.IntentCompat.getParcelableArrayListExtra(
            send, Intent.EXTRA_STREAM, android.net.Uri::class.java,
        )!!
        uris.size shouldBe 2
        uris.map { it.lastPathSegment }.toSet() shouldBe setOf("Ticket A", "Ticket B")
    }

    @Test
    fun bulkTags_cancelDiscardsChangesAndSavePreservesOtherTags() = runTest {
        seedDocuments()
        val travel = repository.createTag("Travel")
        val work = repository.createTag("Work")
        repository.setTags("Ticket A", setOf(travel))
        selectTickets()
        selectionAction(R.string.tags)
        composeRule.onNode(hasText("Work") and isToggleable()).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.cancel)).performClick()
        repository.documents.first().first { it.name == "Ticket A" }.tags.map { it.id } shouldBe listOf(travel)

        selectionAction(R.string.tags)
        composeRule.onNode(hasText("Work") and isToggleable()).performClick()
        composeRule.activityRule.scenario.recreate()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText(composeRule.activity.getString(R.string.save)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.save)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("2 selected")).fetchSemanticsNodes().isEmpty()
        }
        val documents = repository.documents.first { updated ->
            val tagIds = updated.associate { document -> document.name to document.tags.map { it.id }.toSet() }
            tagIds["Ticket A"] == setOf(travel, work) && tagIds["Ticket B"] == setOf(work)
        }.associateBy { it.name }
        documents.getValue("Ticket A").tags.map { it.id }.toSet() shouldBe setOf(travel, work)
        documents.getValue("Ticket B").tags.map { it.id } shouldBe listOf(work)
        documents.getValue("Other document").tags shouldBe emptyList()
    }

    @Test
    fun openDocument_explainsWhenThePdfCantBeOpened() {
        // the seeded documents are text files, not PDFs
        seedDocuments()

        composeRule.onNodeWithText("Ticket A").performClick()

        val error = composeRule.activity.getString(R.string.error_showing_document)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText(error)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(error).assertIsDisplayed()
    }

    @Test
    fun openApp_showsEmptyState() {
        val context = composeRule.activity
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText(context.getString(R.string.import_pdf)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(context.getString(R.string.empty_state_title)).assertIsDisplayed()
    }

    @Test
    fun launcher_hasOneEntryPointingToMainActivity() {
        val context = composeRule.activity
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.packageName)

        val launcher = context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo }
            .single { it.name == MainActivity::class.java.name || it.targetActivity == MainActivity::class.java.name }

        launcher.targetActivity shouldBe MainActivity::class.java.name
    }
}
