package com.iknalos.cardvault

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class EditCardActivity : AppCompatActivity() {

    private lateinit var cards: MutableList<Card>
    private var editing: Card? = null
    private var selectedColor = CardStore.COLORS[0]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        cards = CardStore.load(this)
        editing = intent.getStringExtra("card_id")?.let { id -> cards.firstOrNull { it.id == id } }

        val nameField = findViewById<EditText>(R.id.fName)
        val valueField = findViewById<EditText>(R.id.fValue)
        val formatSpinner = findViewById<Spinner>(R.id.fFormat)
        val notesField = findViewById<EditText>(R.id.fNotes)

        formatSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Barcodes.labels)

        val card = editing
        if (card != null) {
            findViewById<TextView>(R.id.editTitle).text = "Edit card"
            nameField.setText(card.name)
            valueField.setText(card.value)
            notesField.setText(card.notes)
            selectedColor = card.color
            formatSpinner.setSelection(Barcodes.bcids.indexOf(card.format).coerceAtLeast(0))
            findViewById<View>(R.id.deleteBtn).visibility = View.VISIBLE
        } else {
            valueField.setText(intent.getStringExtra("prefill_value") ?: "")
            val prefillFormat = intent.getStringExtra("prefill_format") ?: "code128"
            formatSpinner.setSelection(Barcodes.bcids.indexOf(prefillFormat).coerceAtLeast(0))
            selectedColor = CardStore.COLORS[cards.size % CardStore.COLORS.size]
        }

        buildColorRow()
        updatePreview()

        valueField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { updatePreview() }
        })
        formatSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { updatePreview() }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        findViewById<View>(R.id.saveBtn).setOnClickListener { saveCard() }
        findViewById<View>(R.id.cancelBtn).setOnClickListener { finish() }
        findViewById<View>(R.id.deleteBtn).setOnClickListener { confirmDelete() }
    }

    private fun currentBcid(): String =
        Barcodes.bcids[findViewById<Spinner>(R.id.fFormat).selectedItemPosition]

    private fun updatePreview() {
        val value = findViewById<EditText>(R.id.fValue).text.toString().trim()
        val image = findViewById<ImageView>(R.id.previewImage)
        val err = findViewById<TextView>(R.id.previewErr)
        err.text = ""
        if (value.isEmpty()) { image.visibility = View.GONE; return }
        try {
            image.setImageBitmap(Barcodes.render(value, currentBcid()))
            image.visibility = View.VISIBLE
        } catch (e: Exception) {
            image.visibility = View.GONE
            err.text = "Value doesn't fit this barcode type — check the type or the digits."
        }
    }

    private fun buildColorRow() {
        val row = findViewById<LinearLayout>(R.id.colorRow)
        row.removeAllViews()
        CardStore.COLORS.forEach { colorHex ->
            val v = View(this)
            val size = (44 * resources.displayMetrics.density).toInt()
            val margin = (5 * resources.displayMetrics.density).toInt()
            val lp = LinearLayout.LayoutParams(size, size)
            lp.setMargins(margin, 0, margin, 0)
            v.layoutParams = lp
            val d = GradientDrawable()
            d.shape = GradientDrawable.OVAL
            d.setColor(Color.parseColor(colorHex))
            if (colorHex == selectedColor) {
                d.setStroke((3 * resources.displayMetrics.density).toInt(), Color.WHITE)
            }
            v.background = d
            v.setOnClickListener { selectedColor = colorHex; buildColorRow() }
            row.addView(v)
        }
    }

    private fun saveCard() {
        val name = findViewById<EditText>(R.id.fName).text.toString().trim()
        val value = findViewById<EditText>(R.id.fValue).text.toString().trim()
        val notes = findViewById<EditText>(R.id.fNotes).text.toString().trim()
        val format = currentBcid()

        if (name.isEmpty()) { Toast.makeText(this, "Give the card a name", Toast.LENGTH_SHORT).show(); return }
        if (value.isEmpty()) { Toast.makeText(this, "Enter or scan the barcode value", Toast.LENGTH_SHORT).show(); return }
        try { Barcodes.render(value, format) } catch (e: Exception) {
            Toast.makeText(this, "That value can't be encoded as ${Barcodes.labelFor(format)}", Toast.LENGTH_LONG).show()
            return
        }

        val card = editing
        if (card != null) {
            card.name = name; card.value = value; card.format = format
            card.notes = notes; card.color = selectedColor
        } else {
            cards.add(Card(CardStore.newId(), name, value, format, notes, selectedColor, CardStore.nowIso()))
        }
        CardStore.save(this, cards)
        finish()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete this card?")
            .setMessage("This can't be undone.")
            .setPositiveButton("Delete") { _, _ ->
                cards.removeAll { it.id == editing?.id }
                CardStore.save(this, cards)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
