package me.d3sox.pdfwallet.details

import me.d3sox.pdfwallet.db.CertificateDao
import me.d3sox.pdfwallet.db.DocumentPosition
import me.d3sox.pdfwallet.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the barcode and PDF page each document was last shown at.
 */
@Singleton
class DocumentPositions @Inject constructor(
    private val certificateDao: CertificateDao,
    // saving outlives the screen, which is usually closed right after the last swipe
    @ApplicationScope private val scope: CoroutineScope,
) {
    suspend fun get(id: String): DocumentPosition? = certificateDao.position(id)

    fun save(id: String, barcode: Int, page: Int) = scope.launch {
        certificateDao.savePosition(DocumentPosition(id, barcode, page))
    }
}
