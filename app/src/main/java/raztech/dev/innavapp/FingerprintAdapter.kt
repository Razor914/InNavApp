package raztech.dev.innavapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class FingerprintAdapter(
    private val onItemClick: (FingerprintActivity.FingerprintFull) -> Unit
) : ListAdapter<FingerprintActivity.FingerprintFull, FingerprintAdapter.VH>(DIFF) {

    object DIFF : DiffUtil.ItemCallback<FingerprintActivity.FingerprintFull>() {
        override fun areItemsTheSame(
            oldItem: FingerprintActivity.FingerprintFull,
            newItem: FingerprintActivity.FingerprintFull
        ) = oldItem.key == newItem.key && oldItem.ruangan == newItem.ruangan

        override fun areContentsTheSame(
            oldItem: FingerprintActivity.FingerprintFull,
            newItem: FingerprintActivity.FingerprintFull
        ) = oldItem == newItem
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvRoom = view.findViewById<TextView>(R.id.tvRoom)
        private val tvSsid = view.findViewById<TextView>(R.id.tvSsid)
        private val tvBssid = view.findViewById<TextView>(R.id.tvBssid)
        private val tvRssi = view.findViewById<TextView>(R.id.tvRssi)
        private val tvLokasi = view.findViewById<TextView>(R.id.tvLokasi)

        fun bind(item: FingerprintActivity.FingerprintFull) {
            val d = item.data
            tvRoom.text = "Ruangan: ${item.ruangan}"
            tvSsid.text = "SSID: ${d.ssid ?: "-"}"
            tvBssid.text = "BSSID: ${d.bssid ?: "-"}"
            tvRssi.text = "RSSI: ${(d.rssi ?: 0)} dBm"
            tvLokasi.text = "Lokasi: ${d.lokasi ?: "-"}"

            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_fingerprint, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))
}
