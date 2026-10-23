package com.michaeltroger.gruenerpass

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.michaeltroger.gruenerpass.pdfimporter.PdfImportResult
import com.michaeltroger.gruenerpass.pdfimporter.PdfImporter
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PdfImporterTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var importer: PdfImporter

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        importer.deletePendingFile()
        createdFiles.forEach(File::delete)
    }

    private fun source(name: String, write: (File) -> Unit): Uri {
        val file = File(context.cacheDir, name).also(write)
        createdFiles += file
        return Uri.fromFile(file)
    }

    private fun asset(name: String): Uri = source(name) { file ->
        instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
    }

    private fun PdfImportResult.importedFile(): File {
        val pending = shouldBeInstanceOf<PdfImportResult.Success>().pendingCertificate
        return File(context.filesDir, pending.fileName).also { createdFiles += it }
    }

    @Test
    fun regularPdf_isImportedWithItsFileName() = runTest {
        importer.preparePendingFile(asset("demo.pdf"))

        val result = importer.importPendingFile(password = null)

        result.importedFile().exists() shouldBe true
        (result as PdfImportResult.Success).pendingCertificate.documentName shouldBe "demo"
    }

    @Test
    fun protectedPdf_isImportedAfterTheRightPassword() = runTest {
        importer.preparePendingFile(asset("password.pdf"))

        importer.importPendingFile(password = null) shouldBe PdfImportResult.PasswordRequired
        importer.importPendingFile(password = "wrong") shouldBe PdfImportResult.PasswordRequired
        val result = importer.importPendingFile(password = "test")

        result.importedFile().exists() shouldBe true
    }

    @Test
    fun otherFile_failsToImport() = runTest {
        importer.preparePendingFile(source("broken.pdf") { it.writeText("not a PDF") })

        importer.importPendingFile(password = null) shouldBe PdfImportResult.ParsingError
        // the file was discarded
        importer.importPendingFile(password = null) shouldBe PdfImportResult.NoFileToImport
    }
}
