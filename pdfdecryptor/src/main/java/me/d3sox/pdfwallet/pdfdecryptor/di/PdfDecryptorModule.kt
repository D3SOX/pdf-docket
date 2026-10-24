package me.d3sox.pdfwallet.pdfdecryptor.di

import me.d3sox.pdfwallet.pdfdecryptor.PdfDecryptor;
import me.d3sox.pdfwallet.pdfdecryptor.PdfDecryptorImpl;

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
public abstract class PdfDecryptorModule {

    @Binds
    internal abstract fun bindPdfDecryptor(
        pdfDecryptorImpl: PdfDecryptorImpl
    ): PdfDecryptor
}
