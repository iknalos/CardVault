package com.iknalos.cardvault

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

/** Maps between bwip-js bcids (the web app's format identifiers) and ZXing formats. */
object Barcodes {

    /** Pseudo-format for NFC tap cards: value holds the tag UID, nothing is rendered. */
    const val NFC_FORMAT = "nfctag"

    // Insertion order = order shown in the barcode-type dropdown
    val FORMAT_LABELS = linkedMapOf(
        "qrcode" to "QR Code",
        "code128" to "Code 128",
        "code39" to "Code 39",
        "code93" to "Code 93",
        "ean13" to "EAN-13",
        "ean8" to "EAN-8",
        "upca" to "UPC-A",
        "upce" to "UPC-E",
        "interleaved2of5" to "ITF (Interleaved 2 of 5)",
        "rationalizedCodabar" to "Codabar",
        "pdf417" to "PDF417",
        "azteccode" to "Aztec",
        "datamatrix" to "Data Matrix"
    )

    private val ZXING = mapOf(
        "qrcode" to BarcodeFormat.QR_CODE,
        "code128" to BarcodeFormat.CODE_128,
        "code39" to BarcodeFormat.CODE_39,
        "code93" to BarcodeFormat.CODE_93,
        "ean13" to BarcodeFormat.EAN_13,
        "ean8" to BarcodeFormat.EAN_8,
        "upca" to BarcodeFormat.UPC_A,
        "upce" to BarcodeFormat.UPC_E,
        "interleaved2of5" to BarcodeFormat.ITF,
        "rationalizedCodabar" to BarcodeFormat.CODABAR,
        "pdf417" to BarcodeFormat.PDF_417,
        "azteccode" to BarcodeFormat.AZTEC,
        "datamatrix" to BarcodeFormat.DATA_MATRIX
    )

    private val SQUARE = setOf("qrcode", "azteccode", "datamatrix")

    val bcids: List<String> get() = FORMAT_LABELS.keys.toList()
    val labels: List<String> get() = FORMAT_LABELS.values.toList()

    fun labelFor(bcid: String): String =
        if (bcid == NFC_FORMAT) "NFC tap card" else FORMAT_LABELS[bcid] ?: bcid

    /** ZXing scan-result format name (e.g. "CODE_128") -> bcid; defaults to code128. */
    fun bcidFromZxingName(name: String?): String =
        ZXING.entries.firstOrNull { it.value.name == name }?.key ?: "code128"

    /** Renders the barcode as a bitmap. Throws if the value can't be encoded in this format. */
    fun render(value: String, bcid: String): Bitmap {
        val fmt = ZXING[bcid] ?: throw IllegalArgumentException("Unknown format $bcid")
        val square = bcid in SQUARE
        val w = if (square) 800 else 1200
        val h = when {
            square -> 800
            bcid == "pdf417" -> 480
            else -> 360
        }
        val hints = mapOf(EncodeHintType.MARGIN to 2)
        val matrix = MultiFormatWriter().encode(value, fmt, w, h, hints)
        val bw = matrix.width
        val bh = matrix.height
        // Fill a pixel array and blit once, rather than one JNI setPixel per pixel.
        val pixels = IntArray(bw * bh)
        for (y in 0 until bh) {
            val row = y * bw
            for (x in 0 until bw) {
                pixels[row + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565)
        bmp.setPixels(pixels, 0, bw, 0, 0, bw, bh)
        return bmp
    }

    /** True if the value can be encoded in this format, without building a bitmap. */
    fun canEncode(value: String, bcid: String): Boolean {
        val fmt = ZXING[bcid] ?: return false
        return try {
            MultiFormatWriter().encode(value, fmt, 200, 200, mapOf(EncodeHintType.MARGIN to 2))
            true
        } catch (e: Exception) {
            false
        }
    }
}
