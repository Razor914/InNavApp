package raztech.dev.innavapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.*

class UserActivity : AppCompatActivity() {

    // UI
    private lateinit var wifiManager: WifiManager
    private lateinit var btnDetect: Button
    private lateinit var tvResult: TextView
    private lateinit var marker: View
    private lateinit var imgDenah: ImageView

    // Firebase
    private lateinit var fingerprintRef: DatabaseReference
    private lateinit var mappingRef: DatabaseReference
    private var mappingListener: ValueEventListener? = null

    private val fingerprintList = mutableListOf<Fingerprint>()
    private val mappingPosisi = mutableMapOf<String, Pair<Float, Float>>() // label -> (x,y) [0..1]

    // Scan & cooldown
    private var scanCooldownTimer: CountDownTimer? = null
    private val REQ_WIFI_PERMS = 101

    // BroadcastReceiver Wi‑Fi
    private lateinit var scanReceiver: BroadcastReceiver
    private var isReceiverRegistered = false

    // Filter agar hanya memproses scan yang kita minta sendiri
    private var awaitingScanResult = false
    private var lastScanStartMs: Long = 0L
    private val ACCEPT_WINDOW_MS = 10_000L // terima hasil ≤ 10s setelah startScan()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user)

        // Init UI
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        btnDetect = findViewById(R.id.btnLocate)
        tvResult = findViewById(R.id.tvLocation)
        marker = findViewById(R.id.markerView)
        imgDenah = findViewById(R.id.imgDenah)

        // Firebase refs
        fingerprintRef = FirebaseDatabase.getInstance().getReference("Fingerprint")
        mappingRef = FirebaseDatabase.getInstance().getReference("Denah").child("mapping")

        // Load data
        ambilFingerprintFirebase()

        // Tombol manual
        btnDetect.setOnClickListener { checkAndScan() }

        // Tombol back
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        // Receiver hasil scan (manual only)
        scanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return

                // Cek izin di scope ini
                val hasFine = ActivityCompat.checkSelfPermission(
                    this@UserActivity, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasNearby = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    ActivityCompat.checkSelfPermission(
                        this@UserActivity, Manifest.permission.NEARBY_WIFI_DEVICES
                    ) == PackageManager.PERMISSION_GRANTED
                } else true
                if (!hasFine || !hasNearby) return

                // Pastikan ini hasil scan yang kita minta (bukan scan sistem)
                val now = SystemClock.elapsedRealtime()
                val resultsUpdated = intent?.getBooleanExtra(
                    WifiManager.EXTRA_RESULTS_UPDATED, false
                ) ?: false
                val withinWindow = (now - lastScanStartMs) <= ACCEPT_WINDOW_MS
                val shouldProcess = awaitingScanResult && (resultsUpdated || withinWindow)
                if (!shouldProcess) return

                try {
                    val results = wifiManager.scanResults
                    awaitingScanResult = false // satu batch diproses
                    prosesHasilScan(results)
                } catch (_: SecurityException) {
                    Toast.makeText(
                        this@UserActivity, "SecurityException saat membaca hasil scan.", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // Register receiver
        if (!isReceiverRegistered) {
            registerReceiver(scanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
            isReceiverRegistered = true
        }

        // Dengarkan perubahan mapping koordinat
        mappingListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                mappingPosisi.clear()
                for (child in snapshot.children) {
                    val label = child.key ?: continue
                    val x = child.child("x").getValue(Double::class.java)?.toFloat() ?: continue
                    val y = child.child("y").getValue(Double::class.java)?.toFloat() ?: continue
                    mappingPosisi[label] = Pair(x, y)
                }
            }
            override fun onCancelled(error: DatabaseError) { /* no-op */ }
        }
        mappingListener?.let { mappingRef.addValueEventListener(it) }
    }

    override fun onStop() {
        super.onStop()
        // Unregister receiver
        if (isReceiverRegistered) {
            unregisterReceiver(scanReceiver)
            isReceiverRegistered = false
        }
        // Lepas listener
        mappingListener?.let { mappingRef.removeEventListener(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanCooldownTimer?.cancel()
    }

    /** ===== Permission helpers ===== */
    private fun hasWifiScanPermission(): Boolean {
        val hasFine = ActivityCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasNearby = if (android.os.Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.checkSelfPermission(
                this, Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        return hasFine && hasNearby
    }

    private fun requestWifiScanPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ),
                REQ_WIFI_PERMS
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQ_WIFI_PERMS
            )
        }
    }

    private fun checkAndScan() {
        if (!hasWifiScanPermission()) {
            requestWifiScanPermission()
            return
        }
        scanAndPredict()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_WIFI_PERMS) {
            if (hasWifiScanPermission()) {
                scanAndPredict() // cooldown dimulai di dalam bila startScan berhasil
            } else {
                Toast.makeText(this, "Izin Wi‑Fi/Location ditolak.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** ===== Ambil fingerprint dari Firebase ===== */
    private fun ambilFingerprintFirebase() {
        fingerprintRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                fingerprintList.clear()
                for (lokasiSnap in snapshot.children) {
                    val parentLokasi = lokasiSnap.key?.trim()
                    for (fingerSnap in lokasiSnap.children) {
                        val data = fingerSnap.getValue(Fingerprint::class.java)
                        if (data != null) {
                            if (data.lokasi.isNullOrBlank()) data.lokasi = parentLokasi
                            fingerprintList.add(data)
                        }
                    }
                }
                Toast.makeText(
                    this@UserActivity,
                    "Fingerprint siap (${fingerprintList.size} data)",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@UserActivity,
                    "Gagal ambil fingerprint: ${error.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    /** ===== Mulai scan Wi‑Fi; hasil diproses di receiver ===== */
    private fun scanAndPredict() {
        if (!hasWifiScanPermission()) {
            Toast.makeText(this, "Izin Wi‑Fi/Location belum diberikan.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, "Wi‑Fi dimatikan. Nyalakan dulu.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val started = wifiManager.startScan()
            if (!started) {
                Toast.makeText(this, "Gagal memulai scan (mungkin dibatasi OS).", Toast.LENGTH_SHORT).show()
                return
            }
            // Hanya bila startScan berhasil → tandai menunggu hasil & mulai cooldown
            lastScanStartMs = SystemClock.elapsedRealtime()
            awaitingScanResult = true
            startCooldown()
        } catch (_: SecurityException) {
            Toast.makeText(this, "SecurityException saat memulai scan.", Toast.LENGTH_SHORT).show()
        }
    }

    /** ===== Proses hasil scan → prediksi → tampilkan marker ===== */
    private fun prosesHasilScan(results: List<ScanResult>) {
        if (results.isEmpty() || fingerprintList.isEmpty()) {
            tvResult.text = "Fingerprint belum tersedia atau Wi‑Fi tidak terdeteksi."
            marker.visibility = View.GONE
            return
        }
        val lokasi = predictLocationWKNN_PerLokasi(results, fingerprintList, k = 3).first
        tvResult.text = "Anda berada di posisi: $lokasi"
        tampilkanMarker(lokasi)
    }

    /** ===== Tampilkan marker di denah ===== */
    private fun tampilkanMarker(lokasi: String) {
        val pos = mappingPosisi[lokasi]
        if (pos == null) {
            marker.visibility = View.GONE
            tvResult.append(" (mapping koordinat belum ada)")
            return
        }
        imgDenah.post {
            val w = imgDenah.width.toFloat()
            val h = imgDenah.height.toFloat()
            val x = pos.first * w - marker.width / 2f
            val y = pos.second * h - marker.height / 2f
            marker.setBackgroundResource(R.drawable.marker_label)
            marker.visibility = View.VISIBLE
            marker.x = x
            marker.y = y
        }
    }

    /** ===== WKNN per‑lokasi + confidence (return Pair<label, margin]) ===== */
    private fun predictLocationWKNN_PerLokasi(
        currentScan: List<ScanResult>,
        database: List<Fingerprint>,
        k: Int = 3,
        rssiMissing: Int = -95
    ): Pair<String, Double> {
        val cleanedDb = database.filter {
            it.lokasi?.isNotBlank() == true && it.bssid?.isNotBlank() == true && it.rssi != null
        }
        if (cleanedDb.isEmpty()) return "Tidak diketahui" to 0.0

        val allBssid: Set<String> = cleanedDb.map { it.bssid!!.lowercase() }.toSet()
        val currentMap: Map<String, Int> = currentScan.associate { it.BSSID.lowercase() to it.level }
        val perLokasi: Map<String, List<Fingerprint>> = cleanedDb.groupBy { it.lokasi!!.trim() }

        val jarakPerLokasi = mutableListOf<Pair<String, Double>>()

        for ((lokasi, entries) in perLokasi) {
            val avgRssiByBssid: Map<String, Double> = entries
                .groupBy { it.bssid!!.lowercase() }
                .mapValues { (_, list) ->
                    val vals = list.map { it.rssi!! }
                    if (vals.isEmpty()) rssiMissing.toDouble() else vals.average()
                }

            var sum = 0.0
            for (bssid in allBssid) {
                val fp = avgRssiByBssid[bssid] ?: rssiMissing.toDouble()
                val cur = (currentMap[bssid] ?: rssiMissing).toDouble()
                val diff = cur - fp
                sum += diff * diff
            }
            val dist = kotlin.math.sqrt(sum)
            jarakPerLokasi.add(lokasi to dist)
        }

        if (jarakPerLokasi.isEmpty()) return "Tidak diketahui" to 0.0

        val kNearest = jarakPerLokasi.sortedBy { it.second }.take(k)
        val skor = mutableMapOf<String, Double>()
        for ((lok, d) in kNearest) {
            val w = if (d == 0.0) 1e9 else 1.0 / d
            skor[lok] = skor.getOrDefault(lok, 0.0) + w
        }
        val sorted = skor.entries.sortedByDescending { it.value }
        val top = sorted.getOrNull(0)
        val second = sorted.getOrNull(1)
        val margin = if (top != null && second != null) {
            val num = top.value - second.value
            val den = top.value + second.value
            if (den == 0.0) 0.0 else (num / den).coerceIn(0.0, 1.0)
        } else 1.0
        val label = top?.key ?: "Tidak diketahui"
        return label to margin
    }

    /** ===== Cooldown tombol 2 menit (warna abu‑abu) ===== */
    private fun startCooldown() {
        btnDetect.isEnabled = false
        btnDetect.setBackgroundColor(colorCompat(R.color.gray))

        scanCooldownTimer?.cancel()
        scanCooldownTimer = object : CountDownTimer(2 * 60 * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = (millisUntilFinished / 1000) % 60
                btnDetect.text = String.format("Mohon tunggu selama %d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                btnDetect.isEnabled = true
                btnDetect.text = "Anda berada di posisi: "
                btnDetect.setBackgroundColor(colorCompat(R.color.blue))
            }
        }.start()
    }

    /** ===== Utils ===== */
    private fun colorCompat(colorRes: Int): Int =
        ContextCompat.getColor(this, colorRes)
}

