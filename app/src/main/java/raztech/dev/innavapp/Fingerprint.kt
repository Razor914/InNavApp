package raztech.dev.innavapp

data class Fingerprint(
    var lokasi: String? = null,
    var bssid: String? = null,
    var ssid: String? = null,
    var rssi: Int? = null
)