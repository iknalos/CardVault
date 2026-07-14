package com.iknalos.cardvault

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

/**
 * Invisible dispatcher for NFC taps. Reads the tag UID plus a technical
 * fingerprint (off the main thread, since tag I/O blocks), then routes to the
 * announce screen if the card is known, or into the label editor if it's new.
 */
class TapActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        @Suppress("DEPRECATION")
        val tag = intent?.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        val uid = tag?.id?.joinToString("") { "%02x".format(it) }
        if (tag == null || uid.isNullOrEmpty()) { finish(); return }

        // Reading sectors/NDEF can take a moment and must not run on the UI thread.
        thread {
            val details = try { NfcFingerprint.read(tag) } catch (e: Exception) { "" }
            runOnUiThread { route(uid, details) }
        }
    }

    private fun route(uid: String, details: String) {
        val cards = CardStore.load(this)
        val card = cards.firstOrNull { it.format == Barcodes.NFC_FORMAT && it.value.equals(uid, ignoreCase = true) }

        if (card != null) {
            // Refresh the stored fingerprint on each tap (cards can be re-read more fully).
            if (details.isNotEmpty() && details != card.details) {
                card.details = details
                CardStore.save(this, cards)
            }
            startActivity(Intent(this, DisplayActivity::class.java).putExtra("card_id", card.id))
        } else {
            startActivity(
                Intent(this, EditCardActivity::class.java)
                    .putExtra("prefill_value", uid)
                    .putExtra("prefill_format", Barcodes.NFC_FORMAT)
                    .putExtra("prefill_details", details)
            )
        }
        finish()
    }
}
