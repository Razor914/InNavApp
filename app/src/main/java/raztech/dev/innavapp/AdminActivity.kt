package raztech.dev.innavapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class AdminActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var scanButton: Button
    private lateinit var clearButton: Button
    private lateinit var listView: ListView
    private lateinit var wifiAdapter: WifiAdapter
    private lateinit var etLocationLabel: EditText
    private lateinit var firebaseRef: DatabaseReference
    private var scanCooldownTimer: CountDownTimer? = null

    // Data untuk UI (tampilan list)
    private val wifiItems = mutableListOf<WifiItem>()
    data class WifiItem(val ssid: String?, val bssid: String?, val rssi: Int?)

    // Jika Anda punya data class Fingerprint sendiri di project, ini akan dipakai.
    // (Misal: data class Fingerprint(val lokasi:String?=null, val bssid:String?=null, val ssid:String?=null, val rssi:Int?=null))
    private val fingerprints = mutableListOf<Fingerprint>()

    // Multiple scan untuk rata-rata
    private val allResults = mutableListOf<List<ScanResult>>()
    private val scanCount = 4

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) handleScanSuccess() else handleScanFailure()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        firebaseRef = FirebaseDatabase.getInstance().getReference("Fingerprint")

        scanButton = findViewById(R.id.scanButton)
        listView = findViewById(R.id.wifiListView)
        etLocationLabel = findViewById(R.id.etLocationLabel)
        clearButton = findViewById(R.id.clearButton)

        // EmptyView + adapter kustom
        listView.emptyView = findViewById(R.id.emptyWifi)
        wifiAdapter = WifiAdapter(wifiItems)
        listView.adapter = wifiAdapter

        findViewById<Button>(R.id.btnToFingerprint).setOnClickListener {
            startActivity(Intent(this, FingerprintActivity::class.java))
        }
        findViewById<Button>(R.id.btnToMapping).setOnClickListener {
            startActivity(Intent(this, MappingActivity::class.java))
        }
        clearButton.setOnClickListener {
            allResults.clear()
            wifiItems.clear()
            wifiAdapter.notifyDataSetChanged()
        }

        scanButton.setOnClickListener {
            if (checkLocationPermission()) {
                if (isGpsEnabled()) {
                    allResults.clear()
                    startMultiScan(1)
                    startCooldown()
                } else {
                    Toast.makeText(this, "GPS belum aktif. Aktifkan GPS dulu.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
        }

        // Register receiver
        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(wifiScanReceiver) }
        scanCooldownTimer?.cancel()
    }

    private fun checkLocationPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 100)
            false
        } else true
    }

    private fun isGpsEnabled(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isGpsEnabled()) startMultiScan(1)
                else {
                    Toast.makeText(this, "GPS belum aktif. Aktifkan GPS.", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            } else {
                Toast.makeText(this, "Permission ditolak.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Scan berulang untuk mengambil rata-rata RSSI per BSSID
    private fun startMultiScan(current: Int) {
        if (current == 1) {
            Toast.makeText(this, "Pengambilan data WiFi $scanCount kali, harap diam di titik yang sama...", Toast.LENGTH_SHORT).show()
        }

        val lokasi = etLocationLabel.text.toString()
        if (lokasi.isEmpty()) {
            Toast.makeText(this, "Masukkan nama lokasi.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, "WiFi tidak aktif. Aktifkan WiFi.", Toast.LENGTH_SHORT).show()
            return
        }

        wifiManager.startScan() // hasil akan diterima di wifiScanReceiver

        if (current < scanCount) {
            Handler(Looper.getMainLooper()).postDelayed({ startMultiScan(current + 1) }, 1200)
        } else {
            Handler(Looper.getMainLooper()).postDelayed({ processAverageResults() }, 1500)
        }
    }

    // Setiap selesai scan, tambahkan ke allResults
    private fun handleScanSuccess() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Permission lokasi dibutuhkan.", Toast.LENGTH_SHORT).show()
            return
        }
        allResults.add(wifiManager.scanResults)
    }

    // Proses rata-rata
    private fun processAverageResults() {
        val lokasi = etLocationLabel.text.toString()

        val flat = allResults.flatten()
        val bssidMap = mutableMapOf<String, MutableList<Int>>()
        flat.forEach { sr ->
            bssidMap.getOrPut(sr.BSSID) { mutableListOf() }.add(sr.level)
        }

        // Buat list Fingerprint (menggunakan named args agar aman)
        val avgList: List<Fingerprint> = bssidMap.map { (bssid, rssis) ->
            val rssiAvg = rssis.average().toInt()
            val ssidVal = flat.find { it.BSSID == bssid }?.SSID ?: "(Tidak ada SSID)"
            Fingerprint(
                lokasi = lokasi,
                bssid = bssid,
                ssid  = ssidVal,
                rssi  = rssiAvg
            )
        }

        // Tampilkan ke UI (adapter kustom)
        wifiItems.clear()
        avgList.forEach { fp ->
            wifiItems.add(WifiItem(fp.ssid, fp.bssid, fp.rssi))
        }
        wifiAdapter.notifyDataSetChanged()

        // Simpan ke Firebase
        saveToFirebase(avgList)
        Toast.makeText(this, "Fingerprint rata-rata RSSI disimpan untuk lokasi $lokasi", Toast.LENGTH_SHORT).show()
    }

    private fun handleScanFailure() {
        Toast.makeText(this, "Gagal scan. Coba lagi nanti.", Toast.LENGTH_SHORT).show()
    }

    private fun saveToFirebase(dataList: List<Fingerprint>) {
        val lokasi = etLocationLabel.text.toString()
        val lokasiRef = firebaseRef.child(lokasi)
        dataList.forEach { item ->
            val key = lokasiRef.push().key
            if (key != null) lokasiRef.child(key).setValue(item)
        }
    }

    // Cooldown tombol Scan
    private fun startCooldown() {
        scanButton.isEnabled = false
        tintButton(scanButton, R.color.gray)

        scanCooldownTimer?.cancel()
        scanCooldownTimer = object : CountDownTimer(2 * 60 * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = millisUntilFinished / 1000 / 60
                val seconds = (millisUntilFinished / 1000) % 60
                scanButton.text = String.format("Scan lagi dalam %d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                scanButton.isEnabled = true
                scanButton.text = "Scan WiFi"
                tintButton(scanButton, R.color.blue)
            }
        }.start()
    }

    private fun tintButton(button: Button, colorRes: Int) {
        button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
    }

    /** Adapter kustom untuk ListView */
    private inner class WifiAdapter(private val items: List<WifiItem>) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: layoutInflater.inflate(R.layout.item_wifi, parent, false)
            val tvSsid = row.findViewById<TextView>(R.id.tvSsid)
            val tvBssid = row.findViewById<TextView>(R.id.tvBssid)
            val tvRssi = row.findViewById<TextView>(R.id.tvRssi)

            val item = getItem(position)
            val name = item.ssid?.ifBlank { "(Tidak ada SSID)" } ?: "(Tidak ada SSID)"
            val bssidTxt = item.bssid ?: "-"
            val rssiVal = item.rssi ?: 0

            tvSsid.text = name
            tvBssid.text = bssidTxt
            tvRssi.text = "$rssiVal dBm"

            val color = when {
                rssiVal >= -55 -> 0xFF2E7D32.toInt() // kuat (hijau)
                rssiVal >= -67 -> 0xFF5D67E6.toInt() // sedang (ungu)
                rssiVal >= -75 -> 0xFFF9A825.toInt() // lemah (kuning)
                else           -> 0xFFD32F2F.toInt() // sangat lemah (merah)
            }
            tvRssi.backgroundTintList = ColorStateList.valueOf(color)
            return row
        }
    }
}