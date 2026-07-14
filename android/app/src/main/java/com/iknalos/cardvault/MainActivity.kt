package com.iknalos.cardvault

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : AppCompatActivity() {

    companion object {
        // Unlocked once per process; relaunching the app asks again.
        var unlocked = false
    }

    private var cards: MutableList<Card> = mutableListOf()
    private lateinit var adapter: CardAdapter

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            startActivity(
                Intent(this, EditCardActivity::class.java)
                    .putExtra("prefill_value", result.contents)
                    .putExtra("prefill_format", Barcodes.bcidFromZxingName(result.formatName))
            )
        }
    }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                contentResolver.openOutputStream(uri)?.use {
                    it.write(CardStore.toJson(cards).toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this, "Backup saved (keep it private — it contains your card numbers)", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Could not save backup: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw Exception("empty file")
                val imported = CardStore.fromJson(text)
                var added = 0
                imported.forEach { c ->
                    if (cards.none { it.value == c.value && it.format == c.format }) {
                        c.id = CardStore.newId()
                        cards.add(c)
                        added++
                    }
                }
                CardStore.save(this, cards)
                refresh()
                Toast.makeText(this, "Imported $added card(s)", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "That file isn't a valid CardVault backup", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.inflateMenu(R.menu.main_menu)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menuExport -> { exportLauncher.launch("cardvault-backup-${CardStore.nowIso().substring(0, 10)}.json"); true }
                R.id.menuImport -> { importLauncher.launch(arrayOf("*/*")); true }
                R.id.menuLock -> { toggleLock(); true }
                else -> false
            }
        }

        adapter = CardAdapter()
        val list = findViewById<RecyclerView>(R.id.cardList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<ExtendedFloatingActionButton>(R.id.fabAdd).setOnClickListener { launchScan() }
        findViewById<View>(R.id.unlockBtn).setOnClickListener { showLockPrompt() }

        if (CardStore.isLockEnabled(this) && !unlocked) {
            findViewById<View>(R.id.lockOverlay).visibility = View.VISIBLE
            showLockPrompt()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        cards = CardStore.load(this)
        findViewById<View>(R.id.emptyText).visibility = if (cards.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
        findViewById<MaterialToolbar>(R.id.toolbar).menu.findItem(R.id.menuLock)?.isChecked =
            CardStore.isLockEnabled(this)
    }

    private fun launchScan() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(
                "QR_CODE", "CODE_128", "CODE_39", "CODE_93", "EAN_13", "EAN_8",
                "UPC_A", "UPC_E", "ITF", "CODABAR", "PDF_417", "AZTEC", "DATA_MATRIX"
            )
            .setPrompt("Point the camera at the barcode on your card")
            .setBeepEnabled(false)
            .setOrientationLocked(true)
            .setCaptureActivity(PortraitCaptureActivity::class.java)
        scanLauncher.launch(options)
    }

    // ---------- App lock ----------

    private fun canUseLock(): Boolean =
        BiometricManager.from(this)
            .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    private fun toggleLock() {
        if (CardStore.isLockEnabled(this)) {
            CardStore.setLockEnabled(this, false)
            Toast.makeText(this, "App lock turned off", Toast.LENGTH_SHORT).show()
        } else if (canUseLock()) {
            CardStore.setLockEnabled(this, true)
            unlocked = true
            Toast.makeText(this, "CardVault will now ask for your fingerprint or PIN when opened", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Set up a screen lock (PIN or fingerprint) in your phone settings first", Toast.LENGTH_LONG).show()
        }
        refresh()
    }

    private fun showLockPrompt() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    unlocked = true
                    findViewById<View>(R.id.lockOverlay).visibility = View.GONE
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock CardVault")
            .setSubtitle("Use your fingerprint or screen lock")
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(info)
    }

    // ---------- List adapter ----------

    private inner class CardAdapter : RecyclerView.Adapter<CardAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView = v.findViewById(R.id.cardName)
            val format: TextView = v.findViewById(R.id.cardFormat)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false))

        override fun getItemCount() = cards.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val card = cards[position]
            holder.name.text = card.name
            holder.format.text = Barcodes.labelFor(card.format)
            val color = try { Color.parseColor(card.color) } catch (e: Exception) { Color.parseColor("#6c7bff") }
            (holder.itemView as MaterialCardView).setCardBackgroundColor(color)
            holder.itemView.setOnClickListener {
                startActivity(Intent(this@MainActivity, DisplayActivity::class.java).putExtra("card_id", card.id))
            }
        }
    }
}
