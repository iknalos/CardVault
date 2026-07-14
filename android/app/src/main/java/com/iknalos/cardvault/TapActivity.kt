package com.iknalos.cardvault

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Invisible dispatcher for NFC taps. Reads the tag's UID, then routes to the
 * announce screen if the card is already labeled, or straight into the label
 * editor if it's new. Finishes immediately so it never shows its own UI.
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
        if (uid.isNullOrEmpty()) { finish(); return }

        val card = CardStore.load(this)
            .firstOrNull { it.format == Barcodes.NFC_FORMAT && it.value.equals(uid, ignoreCase = true) }

        if (card != null) {
            startActivity(Intent(this, DisplayActivity::class.java).putExtra("card_id", card.id))
        } else {
            startActivity(
                Intent(this, EditCardActivity::class.java)
                    .putExtra("prefill_value", uid)
                    .putExtra("prefill_format", Barcodes.NFC_FORMAT)
            )
        }
        finish()
    }
}
