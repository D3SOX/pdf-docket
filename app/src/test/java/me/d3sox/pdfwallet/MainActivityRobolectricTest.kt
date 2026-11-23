package me.d3sox.pdfwallet

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.BackEventCompat
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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import me.d3sox.pdfwallet.coroutines.dispatcher.di.CoroutineDispatchersModule
import me.d3sox.pdfwallet.db.di.DatabaseModule
import me.d3sox.pdfwallet.documents.DocumentFilter
import me.d3sox.pdfwallet.documents.DocumentRepository
import me.d3sox.pdfwallet.pdfimporter.PendingCertificate
import me.d3sox.pdfwallet.quicksettings.DocumentTileService
import me.d3sox.pdfwallet.settings.SettingsRepository
import me.d3sox.pdfwallet.settings.SortOrder
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import javax.inject.Inject

private const val DRAG_STEPS = 10

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
    @Inject lateinit var settingsRepository: SettingsRepository

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
    fun archive_keepsTheDocumentListVisibleDuringNavigation() {
        seedDocuments()
        composeRule.onNode(
            hasContentDescription(composeRule.activity.getString(R.string.more_options)) and
                !hasAnyAncestor(hasScrollAction()),
        ).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.archive_title)).assertIsDisplayed()
        composeRule.mainClock.autoAdvance = false

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.archive_title)).performClick()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.back)).assertExists()
        // Both screens must exist while the archive slides over the document list.
        composeRule.onAllNodes(hasText("Ticket A")).fetchSemanticsNodes().isNotEmpty() shouldBe true
        composeRule.mainClock.autoAdvance = true
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText(composeRule.activity.getString(R.string.archive_empty_title)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.archive_empty_title)).assertIsDisplayed()

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.back)).performClick()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.archive_empty_title)).assertExists()
        composeRule.onAllNodes(hasText("Ticket A")).fetchSemanticsNodes().isNotEmpty() shouldBe true
        composeRule.mainClock.autoAdvance = true
        composeRule.onNodeWithText("Ticket A").assertIsDisplayed()
    }

    @Test
    fun archive_predictiveBackPreviewsTheListAndCanBeCancelledFromEitherEdge() {
        seedDocuments()
        val context = composeRule.activity
        val dispatcher = context.onBackPressedDispatcher
        val archiveTitle = context.getString(R.string.archive_empty_title)
        listOf(BackEventCompat.EDGE_LEFT, BackEventCompat.EDGE_RIGHT).forEach { edge ->
            selectionAction(R.string.archive_title)
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodes(hasText(archiveTitle)).fetchSemanticsNodes().isNotEmpty()
            }
            val original = composeRule.onNodeWithText(archiveTitle).fetchSemanticsNode().boundsInRoot
            val rootWidth = composeRule.onRoot().fetchSemanticsNode().size.width
            composeRule.runOnIdle {
                dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, edge))
                dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 100f, 0.5f, edge))
            }
            composeRule.waitForIdle()

            composeRule.onAllNodes(hasText("Ticket A")).fetchSemanticsNodes().isNotEmpty() shouldBe true
            val preview = composeRule.onNodeWithText(archiveTitle).fetchSemanticsNode().boundsInRoot
            (preview.width < original.width) shouldBe true
            // shrinking to 90% moves the title by at most a tenth of the width, so only following the finger passes
            (preview.left - original.left > rootWidth * 0.25f) shouldBe true

            composeRule.runOnIdle { dispatcher.dispatchOnBackCancelled() }
            composeRule.onNodeWithText(archiveTitle).assertIsDisplayed()
            composeRule.onAllNodes(hasText("Ticket A")).fetchSemanticsNodes().isEmpty() shouldBe true

            composeRule.runOnIdle {
                dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, edge))
                dispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 100f, 0.8f, edge))
                dispatcher.onBackPressed()
            }
            composeRule.onNodeWithText("Ticket A").assertIsDisplayed()
        }
    }

    @Test
    fun archive_isRestoredAfterRecreationAndShortcutBackReturnsToIt() = runTest {
        seedDocuments()
        repository.archive(listOf("Ticket A"))
        composeRule.runOnUiThread {
            ViewModelProvider(composeRule.activity)[MainViewModel::class.java].onShortcutOpened("Ticket A")
        }
        val context = composeRule.activity
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText(context.getString(R.string.error_showing_document)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.runOnUiThread { context.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText(context.getString(R.string.archive_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Ticket A").assertIsDisplayed()
        composeRule.onAllNodes(hasText("Ticket B")).fetchSemanticsNodes().isEmpty() shouldBe true

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText(context.getString(R.string.archive_title)).assertIsDisplayed()
        composeRule.onNodeWithText("Ticket A").assertIsDisplayed()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("Ticket B").assertIsDisplayed()
    }

    @Test
    fun archive_hidesDocumentsUntilTheyAreUnarchived() = runTest {
        seedDocuments()
        val context = composeRule.activity
        selectTickets()
        selectionAction(R.string.archive)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("Ticket A")).fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("Other document").assertIsDisplayed()

        selectionAction(R.string.archive_title)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("Ticket A")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(hasText("Other document")).fetchSemanticsNodes().isEmpty() shouldBe true
        selectTickets()
        selectionAction(R.string.unarchive)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText(context.getString(R.string.archive_empty_title)))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.runOnUiThread { context.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("Ticket A")).fetchSemanticsNodes().isNotEmpty()
        }
        repository.documents.first().none { it.archived } shouldBe true
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
    fun openDocument_keepsShowingItWhenAnotherOneIsImported() = runTest {
        seedDocuments()
        composeRule.onNodeWithText("Ticket A").performClick()
        val context = composeRule.activity
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText(context.getString(R.string.document_position, 3, 3)))
                .fetchSemanticsNodes().isNotEmpty()
        }

        repository.file("New ticket").writeText("PDF placeholder of New ticket")
        repository.add(PendingCertificate(fileName = "New ticket", documentName = "New ticket"))

        // the new document is listed first, before the open one
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText(context.getString(R.string.document_position, 4, 4)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Ticket A").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun wideScreen_showsDocumentsNextToTheListAndTurnsToThePickedOne() {
        seedDocuments()
        val context = composeRule.activity
        val waitForPosition = { index: Int ->
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodes(hasText(context.getString(R.string.document_position, index, 3)))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.choose_document)).assertIsDisplayed()

        composeRule.onNodeWithText("Ticket A").performClick()
        waitForPosition(3)
        // the list stays, with the open document highlighted
        composeRule.onNodeWithContentDescription(context.getString(R.string.import_pdf)).assertIsDisplayed()
        composeRule.onNode(hasText("Ticket A") and hasAnyAncestor(hasScrollAction())).assertIsSelected()

        composeRule.onNodeWithText("Ticket B").performClick()
        waitForPosition(2)
        composeRule.onNode(hasText("Ticket B") and hasAnyAncestor(hasScrollAction())).assertIsSelected()
        composeRule.onNode(hasText("Ticket A") and hasAnyAncestor(hasScrollAction())).assertIsNotSelected()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText(context.getString(R.string.choose_document)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.import_pdf)).assertIsDisplayed()
    }

    private fun waitForPosition(index: Int, count: Int = 3) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText(composeRule.activity.getString(R.string.document_position, index, count)))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun wideScreen_shortcutTurnsTheDetailsBackToItsDocument() {
        seedDocuments()
        composeRule.onNodeWithText("Ticket A").performClick()
        waitForPosition(3)
        composeRule.onNodeWithText("Ticket B").performClick()
        waitForPosition(2)

        composeRule.runOnUiThread {
            ViewModelProvider(composeRule.activity)[MainViewModel::class.java].onShortcutOpened("Ticket A")
        }
        waitForPosition(3)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    fun wideScreen_openingTheArchiveClosesTheOpenDocument() {
        seedDocuments()
        val context = composeRule.activity
        composeRule.onNodeWithText("Ticket A").performClick()
        waitForPosition(3)

        // the list's menu, the one of the details is further right
        val menus = composeRule.onAllNodes(
            hasContentDescription(context.getString(R.string.more_options)) and !hasAnyAncestor(hasScrollAction()),
        )
        val listMenu = menus.fetchSemanticsNodes().withIndex().minBy { it.value.boundsInRoot.left }.index
        menus[listMenu].performClick()
        composeRule.onNodeWithText(context.getString(R.string.archive)).performClick()

        composeRule.onNodeWithText(context.getString(R.string.archive_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.choose_document)).assertIsDisplayed()
    }

    @Test
    fun documentMenu_picksTheDocumentOfTheQuickSettingsTile() = runTest {
        seedDocuments()
        val context = composeRule.activity
        val openMenu = {
            composeRule.onAllNodes(
                hasContentDescription(context.getString(R.string.more_options)) and hasAnyAncestor(hasScrollAction()),
            )[0].performClick()
        }
        val firstDocument = repository.documents.first().first()

        openMenu()
        composeRule.onNodeWithText(context.getString(R.string.add_to_quick_settings)).performClick()
        // Android 11 can't offer to add the tile
        composeRule.onNodeWithText(context.getString(R.string.quick_settings_tile_hint)).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { settingsRepository.settings.first() }.quickSettingsDocumentId == firstDocument.id
        }

        val tileState = {
            context.packageManager.getComponentEnabledSetting(ComponentName(context, DocumentTileService::class.java))
        }
        openMenu()
        composeRule.onNodeWithText(context.getString(R.string.remove_from_quick_settings)).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { settingsRepository.settings.first() }.quickSettingsDocumentId == null
        }
        // apps can't remove their tile, so it's hidden by disabling its service
        tileState() shouldBe PackageManager.COMPONENT_ENABLED_STATE_DISABLED

        openMenu()
        composeRule.onNodeWithText(context.getString(R.string.add_to_quick_settings)).performClick()
        tileState() shouldBe PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    }

    @Test
    fun manualOrder_holdAndDragMovesADocumentWithoutSelectingIt() = runTest {
        seedDocuments()
        settingsRepository.update { it.copy(sortOrder = SortOrder.MANUAL, manualSortExplained = true) }
        composeRule.waitForIdle()
        val from = composeRule.onNodeWithText("Other document").fetchSemanticsNode().boundsInRoot.center
        val to = composeRule.onNodeWithText("Ticket A").fetchSemanticsNode().boundsInRoot.center

        composeRule.onNodeWithText("Other document").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis * 2)
            repeat(DRAG_STEPS) { moveBy((to - from) / DRAG_STEPS.toFloat()) }
            up()
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { repository.documents.first() }.map { it.name } ==
                listOf("Ticket B", "Ticket A", "Other document")
        }
        composeRule.onAllNodes(hasText("1 selected")).fetchSemanticsNodes().isEmpty() shouldBe true
    }

    @Test
    fun manualOrder_dragBackToTheSamePlaceKeepsLaterHoldsSelecting() = runTest {
        seedDocuments()
        // saving the same order again doesn't change the list then
        repository.reorder(repository.documents.first().map { it.id })
        settingsRepository.update { it.copy(sortOrder = SortOrder.MANUAL, manualSortExplained = true) }
        composeRule.waitForIdle()
        val from = composeRule.onNodeWithText("Other document").fetchSemanticsNode().boundsInRoot.center
        val to = composeRule.onNodeWithText("Ticket A").fetchSemanticsNode().boundsInRoot.center

        composeRule.onNodeWithText("Other document").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis * 2)
            repeat(DRAG_STEPS) { moveBy((to - from) / DRAG_STEPS.toFloat()) }
            repeat(DRAG_STEPS) { moveBy((from - to) / DRAG_STEPS.toFloat()) }
            up()
        }
        composeRule.onNodeWithText("Ticket B").performTouchInput { longClick() }

        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
        composeRule.onNodeWithText("Ticket B").assertIsSelected()
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
