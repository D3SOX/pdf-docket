package me.d3sox.pdfwallet.documents

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.d3sox.pdfwallet.coroutines.dispatcher.di.CoroutineDispatchersModule
import me.d3sox.pdfwallet.db.Tag
import me.d3sox.pdfwallet.db.di.DatabaseModule
import me.d3sox.pdfwallet.documents.tags.TagIcon
import me.d3sox.pdfwallet.documents.tags.tagIcon
import me.d3sox.pdfwallet.pdfimporter.PendingCertificate
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldBeEmpty
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
class DocumentRepositoryTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var repository: DocumentRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    private suspend fun addDocument(id: String, name: String = id) {
        repository.file(id).writeText("content of $id")
        repository.add(PendingCertificate(fileName = id, documentName = name))
    }

    @Test
    fun add_exposesNameAndFileSize() = runTest {
        addDocument("a.pdf", name = "Ticket")

        val document = repository.documents.first().single()
        document.name shouldBe "Ticket"
        document.sizeBytes shouldBe "content of a.pdf".length.toLong()
        repository.exists("a.pdf") shouldBe true
        repository.exists("b.pdf") shouldBe false
    }

    @Test
    fun rename_changesOnlyTheName() = runTest {
        addDocument("a.pdf", name = "Ticket")

        repository.rename("a.pdf", "Train ticket")

        repository.documents.first().single().name shouldBe "Train ticket"
    }

    @Test
    fun delete_removesDocumentFileAndTagAssignments() = runTest {
        addDocument("a.pdf")
        addDocument("b.pdf")
        val tagId = repository.createTag("Travel")
        repository.setTags("a.pdf", setOf(tagId))

        repository.delete(listOf("a.pdf"))

        repository.documents.first().map { it.id } shouldContainExactly listOf("b.pdf")
        repository.file("a.pdf").exists().shouldBeFalse()
        // the tag itself stays
        repository.tags.first() shouldContainExactly listOf(Tag(tagId, "Travel"))
    }

    @Test
    fun setTags_replacesTheAssignedTags() = runTest {
        addDocument("a.pdf")
        val travel = repository.createTag("Travel")
        val work = repository.createTag("Work")
        repository.setTags("a.pdf", setOf(travel))

        repository.setTags("a.pdf", setOf(work))

        repository.documents.first().single().tags shouldContainExactly listOf(Tag(work, "Work"))
    }

    @Test
    fun updateTag_setsAndClearsTheIcon() = runTest {
        val tagId = repository.createTag("Travel")

        repository.updateTag(Tag(tagId, "Travel", TagIcon.TRAIN.name))
        repository.tags.first().single().tagIcon shouldBe TagIcon.TRAIN

        repository.updateTag(Tag(tagId, "Travel"))
        repository.tags.first().single().tagIcon shouldBe null
    }

    @Test
    fun createTag_ignoresDuplicateNames() = runTest {
        repository.createTag("Travel")

        repository.createTag("Travel") shouldBe -1L

        repository.tags.first().map { it.name } shouldContainExactly listOf("Travel")
    }

    @Test
    fun updateTags_preservesMixedTagsAndUnselectedDocuments() = runTest {
        listOf("a.pdf", "b.pdf", "c.pdf").forEach { addDocument(it) }
        val travel = repository.createTag("Travel")
        val work = repository.createTag("Work")
        val ticket = repository.createTag("Ticket")
        repository.setTags("a.pdf", setOf(travel, work))
        repository.setTags("b.pdf", setOf(work))
        repository.setTags("c.pdf", setOf(work))

        repository.updateTags(listOf("a.pdf", "b.pdf"), addedTagIds = setOf(ticket), removedTagIds = setOf(work))

        val documents = repository.documents.first().associateBy { it.id }
        documents.getValue("a.pdf").tags.map { it.id }.toSet() shouldBe setOf(travel, ticket)
        documents.getValue("b.pdf").tags.map { it.id } shouldContainExactly listOf(ticket)
        documents.getValue("c.pdf").tags.map { it.id } shouldContainExactly listOf(work)

        // Re-applying an assignment must not create duplicate tag links.
        repository.updateTags(listOf("a.pdf", "b.pdf"), addedTagIds = setOf(ticket), removedTagIds = emptySet())
        repository.documents.first().first { it.id == "b.pdf" }.tags.map { it.id } shouldContainExactly listOf(ticket)
    }

    @Test
    fun delete_removesOnlySelectedDocuments() = runTest {
        listOf("a.pdf", "b.pdf", "c.pdf").forEach { addDocument(it) }

        repository.delete(listOf("a.pdf", "c.pdf"))

        repository.documents.first().map { it.id } shouldContainExactly listOf("b.pdf")
        repository.file("a.pdf").exists().shouldBeFalse()
        repository.file("c.pdf").exists().shouldBeFalse()
        repository.file("b.pdf").exists() shouldBe true
    }

    @Test
    fun updateTag_keepsNamesUnique() = runTest {
        val travel = repository.createTag("Travel")
        repository.createTag("Work")

        repository.updateTag(Tag(travel, "Work"))

        repository.tags.first().map { it.name } shouldContainExactly listOf("Travel", "Work")
    }

    @Test
    fun deleteTag_removesItFromDocuments() = runTest {
        addDocument("a.pdf")
        val travel = repository.createTag("Travel")
        repository.setTags("a.pdf", setOf(travel))

        repository.deleteTag(travel)

        repository.documents.first().single().tags.shouldBeEmpty()
    }
}
