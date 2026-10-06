package raztech.dev.innavapp

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.database.*

class FingerprintActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var rv: RecyclerView
    private lateinit var loadingOverlay: View
    private lateinit var emptyState: View

    private val items = mutableListOf<FingerprintFull>()
    private lateinit var adapter: FingerprintAdapter

    // Model satu record + info parent
    data class FingerprintFull(val ruangan: String, val key: String, val data: Fingerprint)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fingerprint)

        toolbar = findViewById(R.id.topAppBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        rv = findViewById(R.id.rvFingerprints)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        emptyState = findViewById(R.id.emptyState)

        toolbar.setNavigationOnClickListener { finish() }

        adapter = FingerprintAdapter { item ->
            // klik item → dialog hapus
            AlertDialog.Builder(this)
                .setTitle("Hapus Data?")
                .setMessage(
                    "Ruangan: ${item.ruangan}\n" +
                            "SSID: ${item.data.ssid}\n" +
                            "BSSID: ${item.data.bssid}\n" +
                            "RSSI: ${item.data.rssi}\n" +
                            "Lokasi: ${item.data.lokasi}\n\nHapus data ini?"
                )
                .setPositiveButton("Hapus") { _, _ -> hapusData(item) }
                .setNegativeButton("Batal", null)
                .show()
        }

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        rv.addItemDecoration(VerticalSpaceItemDecoration(8)) // 8px, ubah sesuai selera

        swipeRefresh.setOnRefreshListener { ambilSemuaFingerprint() }

        showLoading(true)
        ambilSemuaFingerprint()
    }

    private fun ambilSemuaFingerprint() {
        val ref = FirebaseDatabase.getInstance().getReference("Fingerprint")
        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val temp = mutableListOf<FingerprintFull>()
                for (ruangan in snapshot.children) {
                    val namaRuangan = ruangan.key ?: continue
                    for (child in ruangan.children) {
                        val data = child.getValue(Fingerprint::class.java) ?: continue
                        val key = child.key ?: continue
                        temp.add(FingerprintFull(namaRuangan, key, data))
                    }
                }
                // tampilkan
                items.clear()
                items.addAll(temp)
                adapter.submitList(items.toList()) // submit salinan list
                updateEmptyState()

                showLoading(false)
                swipeRefresh.isRefreshing = false
            }

            override fun onCancelled(error: DatabaseError) {
                showLoading(false)
                swipeRefresh.isRefreshing = false
                updateEmptyState()
                Toast.makeText(this@FingerprintActivity, "Gagal mengambil data: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun hapusData(item: FingerprintFull) {
        showLoading(true)
        FirebaseDatabase.getInstance().getReference("Fingerprint")
            .child(item.ruangan).child(item.key)
            .removeValue()
            .addOnSuccessListener {
                // hapus dari list & update tampilan
                items.removeAll { it.ruangan == item.ruangan && it.key == item.key }
                adapter.submitList(items.toList())
                updateEmptyState()
                showLoading(false)
                Toast.makeText(this, "Data dihapus", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "Gagal hapus data: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateEmptyState() {
        emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }
}
