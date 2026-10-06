package raztech.dev.innavapp

import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.*

class MappingActivity : AppCompatActivity() {

    private lateinit var btnBack: Button
    private lateinit var listViewMapping: ListView
    private lateinit var imgDenah: ImageView
    private lateinit var markerView: View
    private var lastX: Float = -1f
    private var lastY: Float = -1f

    private val mappingList = mutableListOf<RuanganMapping>()
    private lateinit var mappingAdapter: MappingAdapter

    private val namaGambarDenah = "denah"

    data class RuanganMapping(var label: String = "", var x: Float = 0f, var y: Float = 0f)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mapping)

        btnBack = findViewById(R.id.btnBack)
        listViewMapping = findViewById(R.id.listViewMapping)
        imgDenah = findViewById(R.id.imgDenah)
        markerView = findViewById(R.id.markerView)

        // tampilkan empty view ketika list kosong
        listViewMapping.emptyView = findViewById(R.id.emptyView)

        // set gambar denah
        val resId = resources.getIdentifier(namaGambarDenah, "drawable", packageName)
        imgDenah.setImageResource(resId)

        // siapkan adapter KUSTOM (bukan ArrayAdapter)
        mappingAdapter = MappingAdapter(mappingList)
        listViewMapping.adapter = mappingAdapter

        ambilMappingDariFirebase()

        imgDenah.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
                val imageMatrix = imgDenah.imageMatrix
                val drawable = imgDenah.drawable
                if (drawable != null) {
                    val imgRect = RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
                    imageMatrix.mapRect(imgRect)

                    val clickX = event.x
                    val clickY = event.y

                    if (clickX in imgRect.left..imgRect.right && clickY in imgRect.top..imgRect.bottom) {
                        val relX = (clickX - imgRect.left) / imgRect.width()
                        val relY = (clickY - imgRect.top) / imgRect.height()
                        lastX = relX
                        lastY = relY
                        tampilkanMarker(relX, relY)
                        tampilkanDialogEditMapping(null)
                    }
                }
            }
            true
        }

        listViewMapping.setOnItemClickListener { _, _, pos, _ ->
            tampilkanDialogEditMapping(mappingList[pos])
        }
        btnBack.setOnClickListener { finish() }
    }

    private fun ambilMappingDariFirebase() {
        FirebaseDatabase.getInstance().getReference("Denah").child("mapping")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    mappingList.clear()
                    for (child in snapshot.children) {
                        val label = child.key ?: continue
                        val x = child.child("x").getValue(Double::class.java)?.toFloat() ?: 0f
                        val y = child.child("y").getValue(Double::class.java)?.toFloat() ?: 0f
                        mappingList.add(RuanganMapping(label, x, y))
                    }
                    // Penting: JANGAN set ArrayAdapter di sini. Cukup notify dataset berubah.
                    mappingAdapter.notifyDataSetChanged()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun tampilkanMarker(x: Float, y: Float) {
        imgDenah.post {
            val w = imgDenah.width.toFloat()
            val h = imgDenah.height.toFloat()
            markerView.visibility = View.VISIBLE
            markerView.x = x * w - markerView.width / 2
            markerView.y = y * h - markerView.height / 2
        }
    }

    private fun tampilkanDialogEditMapping(editMapping: RuanganMapping?) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_mapping, null)
        val etLabel = dialogView.findViewById<EditText>(R.id.etLabel)
        val etX = dialogView.findViewById<EditText>(R.id.etX)
        val etY = dialogView.findViewById<EditText>(R.id.etY)

        if (editMapping != null) {
            etLabel.setText(editMapping.label)
            etX.setText("%.4f".format(editMapping.x))
            etY.setText("%.4f".format(editMapping.y))
            lastX = editMapping.x
            lastY = editMapping.y
            tampilkanMarker(editMapping.x, editMapping.y)
        } else if (lastX in 0f..1f && lastY in 0f..1f) {
            etX.setText("%.4f".format(lastX))
            etY.setText("%.4f".format(lastY))
        } else {
            markerView.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (editMapping == null) "Tambah Mapping" else "Edit Mapping")
            .setView(dialogView)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Simpan", null)
            .apply {
                if (editMapping != null) setNeutralButton("Hapus", null)
            }
            .create()

        dialog.setOnShowListener {
            val btnSimpan = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnSimpan.setOnClickListener {
                val label = etLabel.text.toString().trim()
                val x = etX.text.toString().replace(',', '.').toFloatOrNull() ?: -1f
                val y = etY.text.toString().replace(',', '.').toFloatOrNull() ?: -1f
                when {
                    label.isBlank() -> { etLabel.error = "Nama ruangan wajib diisi"; etLabel.requestFocus() }
                    x !in 0f..1f -> { etX.error = "Nilai X antara 0-1"; etX.requestFocus() }
                    y !in 0f..1f -> { etY.error = "Nilai Y antara 0-1"; etY.requestFocus() }
                    else -> {
                        val ref = FirebaseDatabase.getInstance().getReference("Denah")
                            .child("mapping").child(label)
                        ref.child("x").setValue(x)
                        ref.child("y").setValue(y)
                        Toast.makeText(this, "Mapping disimpan", Toast.LENGTH_SHORT).show()
                        ambilMappingDariFirebase()
                        dialog.dismiss()
                    }
                }
            }

            if (editMapping != null) {
                val btnHapus = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                btnHapus.setOnClickListener {
                    AlertDialog.Builder(this)
                        .setTitle("Hapus Mapping?")
                        .setMessage("Hapus mapping untuk \"${editMapping.label}\"?")
                        .setPositiveButton("Hapus") { _, _ ->
                            FirebaseDatabase.getInstance().getReference("Denah")
                                .child("mapping").child(editMapping.label).removeValue()
                            Toast.makeText(this, "Mapping dihapus", Toast.LENGTH_SHORT).show()
                            ambilMappingDariFirebase()
                            dialog.dismiss()
                        }
                        .setNegativeButton("Batal", null)
                        .show()
                }
            }
        }
        dialog.show()
    }

    /** Adapter kustom untuk tampilan list yang rapi **/
    private inner class MappingAdapter(private val items: List<RuanganMapping>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): RuanganMapping = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: LayoutInflater.from(this@MappingActivity)
                .inflate(R.layout.item_mapping, parent, false)

            val tvName = row.findViewById<TextView>(R.id.tvName)
            val tvCoords = row.findViewById<TextView>(R.id.tvCoords)

            val item = getItem(position)
            tvName.text = item.label
            tvCoords.text = String.format("(%.2f, %.2f)", item.x, item.y)

            // sedikit variasi visual
            row.alpha = if (position % 2 == 0) 1.0f else 0.98f
            return row
        }
    }
}
