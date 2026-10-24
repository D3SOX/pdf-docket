package me.d3sox.pdfwallet.barcode.di


import me.d3sox.pdfwallet.barcode.BarcodeRenderer
import me.d3sox.pdfwallet.barcode.BarcodeRendererImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
public abstract class BarcodeModule {

    @Binds
    internal abstract fun bindBarcodeRenderer(
        barcodeRendererImpl: BarcodeRendererImpl
    ): BarcodeRenderer
}
