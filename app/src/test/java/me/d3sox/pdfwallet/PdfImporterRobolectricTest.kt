package me.d3sox.pdfwallet

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import me.d3sox.pdfwallet.coroutines.dispatcher.di.CoroutineDispatchersModule
import me.d3sox.pdfwallet.db.di.DatabaseModule
import me.d3sox.pdfwallet.pdfimporter.PdfImportResult
import me.d3sox.pdfwallet.pdfimporter.PdfImporter
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

@HiltAndroidTest
@UninstallModules(DatabaseModule::class, CoroutineDispatchersModule::class)
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
class PdfImporterRobolectricTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var importer: PdfImporter
    @Inject @ApplicationContext lateinit var context: Context

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun interruptedCopy_leavesNoFileBehind() = runTest {
        val uri = Uri.parse("content://cloud/ticket.pdf")
        // e.g. a cloud provider that aborts the download
        val interrupted = object : InputStream() {
            private var remaining = 1024

            override fun read(): Int = if (remaining-- > 0) 0 else throw IOException("Connection lost")
        }
        shadowOf(context.contentResolver).registerInputStream(uri, interrupted)

        importer.preparePendingFile(uri) shouldBe PdfImportResult.ParsingError

        context.filesDir.listFiles().orEmpty().filter { it.name.endsWith(".pdf") }.shouldBeEmpty()
    }

    @Test
    fun deleteSourceFile_deletesFileOfProvider() = runTest {
        Robolectric.setupContentProvider(DeletingProvider::class.java, "files")

        importer.deleteSourceFile(Uri.parse("content://files/ticket.pdf")) shouldBe true
    }

    @Test
    fun deleteSourceFile_failsWithoutWriteAccess() = runTest {
        Robolectric.setupContentProvider(ReadOnlyProvider::class.java, "shared")

        importer.deleteSourceFile(Uri.parse("content://shared/ticket.pdf")) shouldBe false
    }

    @Test
    fun deleteSourceFile_keepsDocumentsSharedByThisApp() = runTest {
        val file = File(context.filesDir, "stored.pdf").apply { writeText("PDF placeholder") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.pdffileprovider", file)

        importer.deleteSourceFile(uri) shouldBe false
        file.exists() shouldBe true
    }

    @Test
    fun deleteSourceFile_ignoresFileUris() = runTest {
        importer.deleteSourceFile(Uri.parse("file:///sdcard/ticket.pdf")) shouldBe false
    }
}

abstract class TestProvider : ContentProvider() {
    override fun onCreate() = true
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String = "application/pdf"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?) = 0
}

class DeletingProvider : TestProvider() {
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 1
}

class ReadOnlyProvider : TestProvider() {
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw SecurityException("No write access to $uri")
}
