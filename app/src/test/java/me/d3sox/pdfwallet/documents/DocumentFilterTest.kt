package me.d3sox.pdfwallet.documents

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.d3sox.pdfwallet.db.Certificate
import me.d3sox.pdfwallet.db.CertificateDao
import me.d3sox.pdfwallet.db.TagDao
import me.d3sox.pdfwallet.settings.SettingsRepository
import me.d3sox.pdfwallet.settings.SortOrder
import me.d3sox.pdfwallet.settings.TagFilterMode
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import me.d3sox.pdfwallet.coroutines.dispatcher.di.CoroutineDispatchersModule
import me.d3sox.pdfwallet.db.di.DatabaseModule
import me.d3sox.pdfwallet.db.Tag
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import javax.inject.Inject

@HiltAndroidTest
@UninstallModules(DatabaseModule::class, CoroutineDispatchersModule::class)
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class DocumentFilterTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var filter: DocumentFilter
    @Inject lateinit var certificateDao: CertificateDao
    @Inject lateinit var tagDao: TagDao
    @Inject lateinit var settingsRepository: SettingsRepository

    private var ticketTagId = 0L
    private var concertTagId = 0L

    @Before
    fun setUp() = runTest {
        hiltRule.inject()
        certificateDao.insert(Certificate(id = "1.pdf", name = "beta", importedAt = 1))
        certificateDao.insert(Certificate(id = "2.pdf", name = "Alpha", importedAt = 2))
        certificateDao.insert(Certificate(id = "3.pdf", name = "gamma", importedAt = 3))
        ticketTagId = tagDao.insert(Tag(name = "Ticket"))
        concertTagId = tagDao.insert(Tag(name = "Concert"))
        tagDao.setTags("1.pdf", setOf(ticketTagId, concertTagId))
        tagDao.setTags("2.pdf", setOf(ticketTagId))
        filter.clear()
    }

    private suspend fun visibleNames() = filter.documentList.first().visible.map { it.name }

    @Test
    fun sortOrders() = runTest {
        settingsRepository.update { it.copy(sortOrder = SortOrder.IMPORT_DATE_DESC) }
        visibleNames() shouldContainExactly listOf("gamma", "Alpha", "beta")

        settingsRepository.update { it.copy(sortOrder = SortOrder.IMPORT_DATE_ASC) }
        visibleNames() shouldContainExactly listOf("beta", "Alpha", "gamma")

        settingsRepository.update { it.copy(sortOrder = SortOrder.NAME_ASC) }
        visibleNames() shouldContainExactly listOf("Alpha", "beta", "gamma")

        settingsRepository.update { it.copy(sortOrder = SortOrder.NAME_DESC) }
        visibleNames() shouldContainExactly listOf("gamma", "beta", "Alpha")

        settingsRepository.update { it.copy(sortOrder = SortOrder.MANUAL) }
        // documents without a manual order yet are sorted newest first
        visibleNames() shouldContainExactly listOf("gamma", "Alpha", "beta")
        certificateDao.reorder(listOf("1.pdf", "3.pdf", "2.pdf"))
        visibleNames() shouldContainExactly listOf("beta", "gamma", "Alpha")
    }

    @Test
    fun archive_showsOnlyTheArchivedDocuments() = runTest {
        settingsRepository.update { it.copy(sortOrder = SortOrder.NAME_ASC) }
        certificateDao.archive(listOf("1.pdf"))
        visibleNames() shouldContainExactly listOf("Alpha", "gamma")
        // the archived document isn't counted as filtered out
        filter.documentList.first().isFiltered shouldBe false

        filter.setQuery("a")
        filter.show(archive = true)
        filter.documentList.first().archive shouldBe true
        // and the search was cleared
        visibleNames() shouldContainExactly listOf("beta")

        filter.show(archive = false)
        visibleNames() shouldContainExactly listOf("Alpha", "gamma")
    }

    @Test
    fun showArchiveOf_followsTheDocumentAndKeepsTheFilters() = runTest {
        certificateDao.archive(listOf("1.pdf"))
        filter.setQuery("a")

        filter.showArchiveOf("1.pdf")
        filter.documentList.first().let {
            it.archive shouldBe true
            it.query shouldBe "a"
        }

        filter.showArchiveOf("2.pdf")
        filter.documentList.first().archive shouldBe false
    }

    @Test
    fun searchAndTagFilters() = runTest {
        settingsRepository.update { it.copy(sortOrder = SortOrder.NAME_ASC, tagFilterMode = TagFilterMode.ALL) }

        filter.setQuery("ET")
        visibleNames() shouldContainExactly listOf("beta")
        filter.clear()

        filter.toggleTag(ticketTagId)
        visibleNames() shouldContainExactly listOf("Alpha", "beta")
        filter.toggleTag(concertTagId)
        visibleNames() shouldContainExactly listOf("beta")

        settingsRepository.update { it.copy(tagFilterMode = TagFilterMode.ANY) }
        visibleNames() shouldContainExactly listOf("Alpha", "beta")
    }
}
