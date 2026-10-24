package me.d3sox.pdfwallet.pdfimporter.di

import me.d3sox.pdfwallet.pdfimporter.DocumentNameRepo
import me.d3sox.pdfwallet.pdfimporter.DocumentNameRepoImpl
import me.d3sox.pdfwallet.pdfimporter.FileImportRepo
import me.d3sox.pdfwallet.pdfimporter.FileImportRepoImpl
import me.d3sox.pdfwallet.pdfimporter.PdfImporter
import me.d3sox.pdfwallet.pdfimporter.PdfImporterImpl

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
public abstract class PdfImporterModule {

    @Binds
    @Singleton
    internal abstract fun pdfImporter(
        impl: PdfImporterImpl
    ): PdfImporter

    @Binds
    internal abstract fun fileImportRepo(
        impl: FileImportRepoImpl
    ): FileImportRepo

    @Binds
    internal abstract fun documentNameRepo(
        impl: DocumentNameRepoImpl
    ): DocumentNameRepo
}
