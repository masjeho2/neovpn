package com.neovpn.ui

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.neovpn.R
import com.neovpn.dto.QuotaState
import com.neovpn.dto.ProfileItem
import com.neovpn.handler.MmkvManager
import com.neovpn.network.QuotaClient
import com.neovpn.util.QuotaFormat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ServerAdapter(
    private val items: MutableList<Any>,
    private val onServerClick: (ProfileItem) -> Unit,
    private val onMoreClick: (ProfileItem) -> Unit,
    private val onGroupClick: (String) -> Unit = {},
    private val onRefreshGroup: (String) -> Unit = {},
    private val scope: LifecycleOwner? = null,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_SERVER = 1
    }

    private val expandedGroups = mutableSetOf<String>()
    // Track ongoing quota fetch per subGuid → cancel kalau header re-bind sebelum selesai.
    private val quotaJobs = mutableMapOf<String, Job>()
    // Periodic quota refresh job — auto-update tiap 60 detik
    private var quotaPeriodicJob: Job? = null
    // subGuid yang harus skip cache pada fetch berikutnya (tombol refresh)
    private val pendingForceRefresh = mutableSetOf<String>()

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHeader: TextView = view.findViewById(R.id.tv_section_header)
        val tvTimestamp: TextView = view.findViewById(R.id.tv_sub_timestamp)
        val tvQuota: TextView = view.findViewById(R.id.tv_quota)
        val btnRefresh: ImageButton = view.findViewById(R.id.btn_refresh_sub)
        val btnExpand: ImageButton = view.findViewById(R.id.btn_expand)
    }

    class ServerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvProtocol: TextView = view.findViewById(R.id.tv_protocol)
        val tvServerName: TextView = view.findViewById(R.id.tv_server_name)
        val tvServerAddress: TextView = view.findViewById(R.id.tv_server_address)
        val tvLatency: TextView = view.findViewById(R.id.tv_latency)
        val btnMore: ImageButton = view.findViewById(R.id.btn_more)
        val tvActiveBadge: TextView = view.findViewById(R.id.tv_active_badge)
        val viewActiveBar: View = view.findViewById(R.id.view_active_bar)
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is String) TYPE_HEADER else TYPE_SERVER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_section_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_server, parent, false)
                ServerViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> {
                val groupName = items[position] as String
                holder.tvHeader.text = groupName

                // Check if this is a subscription group
                val sub = MmkvManager.getSubscriptionList().find { it.second.remarks == groupName }

                if (sub != null) {
                    // Subscription group - show refresh and timestamp
                    holder.btnRefresh.visibility = View.VISIBLE
                    holder.tvTimestamp.visibility = View.VISIBLE

                    holder.tvTimestamp.text = if (sub.second.lastUpdate > 0) {
                        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                        holder.itemView.context.getString(
                            R.string.updated_at,
                            sdf.format(Date(sub.second.lastUpdate))
                        )
                    } else {
                        holder.itemView.context.getString(R.string.updated_never)
                    }

                    holder.btnRefresh.setOnClickListener { onRefreshGroup(sub.first) }
                    holder.tvHeader.setOnClickListener {
                        val intent = Intent(holder.itemView.context, EditSubscriptionActivity::class.java)
                        intent.putExtra("sub_guid", sub.first)
                        holder.itemView.context.startActivity(intent)
                    }

                    // Quota (NEW): fetch async & bind ke tvQuota.
                    // sub.second.url adalah URL lengkap subscription ("…/<UUID>/<user>/sub").
                    bindQuotaToHeader(holder, sub.first, sub.second.url)
                } else {
                    // Manual servers group — tidak ada subscription URL, sembunyikan quota
                    holder.btnRefresh.visibility = View.GONE
                    holder.tvTimestamp.visibility = View.GONE
                    holder.tvQuota.visibility = View.GONE
                }

                val isExpanded = expandedGroups.contains(groupName)
                holder.btnExpand.animate().cancel()
                holder.btnExpand.rotation = if (isExpanded) 0f else 270f
                holder.btnExpand.setOnClickListener {
                    val nowExpanded = expandedGroups.contains(groupName)
                    if (nowExpanded) expandedGroups.remove(groupName) else expandedGroups.add(groupName)
                    holder.btnExpand.animate()
                        .rotation(if (nowExpanded) 270f else 0f)
                        .setDuration(180)
                        .start()
                    notifyDataSetChanged()
                }
            }
            is ServerViewHolder -> {
                val server = items[position] as ProfileItem

                // Check if parent group is expanded
                val parentGroup = findParentGroup(position)
                if (parentGroup != null && !expandedGroups.contains(parentGroup)) {
                    holder.itemView.visibility = View.GONE
                    holder.itemView.layoutParams = RecyclerView.LayoutParams(0, 0)
                    return
                } else {
                    holder.itemView.visibility = View.VISIBLE
                    holder.itemView.layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                // Protocol badge
                val network = if (server.network.isNotEmpty()) " / ${server.network.uppercase()}" else ""
                holder.tvProtocol.text = "${server.configType.desc}$network"
                holder.tvProtocol.setBackgroundColor(getProtocolColor(server.configType.name))

                holder.tvServerName.text = server.remarks
                holder.tvServerAddress.text = "${server.address}:${server.port}"
                holder.tvLatency.text = if (server.lastLatency > 0) "${server.lastLatency}ms" else "--"

                val selectedGuid = MmkvManager.getSelectedServer()?.guid
                val isActive = server.guid == selectedGuid
                holder.tvActiveBadge.visibility = if (isActive) View.VISIBLE else View.GONE
                holder.viewActiveBar.visibility = if (isActive) View.VISIBLE else View.GONE
                holder.itemView.isSelected = isActive

                if (isActive) {
                    holder.tvServerName.setTextColor(holder.itemView.context.getColor(R.color.accent))
                } else {
                    holder.tvServerName.setTextColor(holder.itemView.context.getColor(R.color.text_primary))
                }

                holder.itemView.setOnClickListener { onServerClick(server) }
                holder.btnMore.setOnClickListener { onMoreClick(server) }
            }
        }
    }

    private fun findParentGroup(position: Int): String? {
        for (i in position downTo 0) {
            if (items[i] is String) return items[i] as String
        }
        return null
    }

    override fun getItemCount(): Int = items.size

    /**
     * Start periodic quota refresh — fetch ulang quota tiap 60 detik.
     * Hanya aktif saat VPN connected; stop saat disconnected.
     */
    fun startPeriodicQuotaRefresh() {
        if (quotaPeriodicJob?.isActive == true) return
        val s = scope ?: return
        quotaPeriodicJob = s.lifecycleScope.launch {
            while (isActive) {
                delay(60_000L)
                android.util.Log.d("ServerAdapter", "periodicQuotaRefresh: rebind all headers")
                notifyDataSetChanged()
            }
        }
    }

    fun stopPeriodicQuotaRefresh() {
        quotaPeriodicJob?.cancel()
        quotaPeriodicJob = null
    }

    /**
     * Force refresh quota untuk subscription tertentu.
     * Dipanggil saat user tekan tombol refresh di header.
     * Invalidate cache + cancel job lama; rebind berikutnya fetch dari network.
     */
    fun forceRefreshQuota(subGuid: String) {
        quotaJobs[subGuid]?.cancel()
        pendingForceRefresh.add(subGuid)
        val url = MmkvManager.getSubscriptionList().find { it.first == subGuid }?.second?.url
        if (!url.isNullOrBlank()) QuotaClient.invalidateForUrl(url)
        android.util.Log.d("ServerAdapter", "forceRefreshQuota: subGuid=$subGuid")
        notifyDataSetChanged()
    }

    fun updateData(servers: List<ProfileItem>) {
        items.clear()

        // Group servers by subscription
        val grouped = servers.groupBy { it.subid.ifEmpty { "manual" } }

        // Always show ALL subscriptions (even with 0 servers)
        val allSubs = MmkvManager.getSubscriptionList()
        for ((subGuid, subItem) in allSubs) {
            val subServers = grouped[subGuid] ?: emptyList()
            expandedGroups.add(subItem.remarks)
            items.add(subItem.remarks)
            items.addAll(subServers)
        }

        // Show manual servers (only if has servers)
        val manualServers = grouped["manual"] ?: emptyList()
        if (manualServers.isNotEmpty()) {
            items.add("Manual Servers")
            items.addAll(manualServers)
        }

        notifyDataSetChanged()
    }

    /**
     * Fetch quota async & bind ke `holder.tvQuota`.
     * - Cancel job sebelumnya untuk subGuid yang sama (kalau sedang fetch).
     * - UNSUPPORTED (URL subscription tidak sesuai pola /quota) → sembunyikan tvQuota.
     */
    private fun bindQuotaToHeader(holder: HeaderViewHolder, subGuid: String, subscriptionUrl: String) {
        if (subscriptionUrl.isBlank()) {
            holder.tvQuota.visibility = View.GONE
            return
        }

        quotaJobs[subGuid]?.cancel()
        val s = scope
        if (s == null) {
            holder.tvQuota.visibility = View.GONE
            return
        }

        val ctx = holder.itemView.context
        holder.tvQuota.text = QuotaFormat.displayText(ctx, QuotaState.LOADING, null)
        holder.tvQuota.setTextColor(ctx.getColor(QuotaFormat.colorRes(QuotaState.LOADING, null)))
        holder.tvQuota.visibility = View.VISIBLE

        val force = pendingForceRefresh.remove(subGuid)
        val job = s.lifecycleScope.launch {
            val appCtx = holder.itemView.context.applicationContext
            val r = QuotaClient.fetchQuota(appCtx, subscriptionUrl, forceRefresh = force)
            android.util.Log.d("ServerAdapter", "bindQuotaToHeader subGuid=$subGuid state=${r.state}")
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION || pos >= items.size) return@launch

            when (r.state) {
                QuotaState.UNSUPPORTED -> {
                    holder.tvQuota.visibility = View.GONE
                }
                else -> {
                    val bindCtx = holder.itemView.context
                    holder.tvQuota.text = QuotaFormat.displayText(bindCtx, r.state, r.data)
                    holder.tvQuota.setTextColor(bindCtx.getColor(QuotaFormat.colorRes(r.state, r.data)))
                    holder.tvQuota.visibility = View.VISIBLE
                }
            }
        }
        quotaJobs[subGuid] = job
    }

    fun toggleGroup(groupName: String) {
        if (expandedGroups.contains(groupName)) {
            expandedGroups.remove(groupName)
        } else {
            expandedGroups.add(groupName)
        }
        notifyDataSetChanged()
    }

    private fun getProtocolColor(protocol: String): Int {
        return when (protocol) {
            "VMESS" -> Color.parseColor("#7C4DFF")
            "VLESS" -> Color.parseColor("#00BCD4")
            "TROJAN" -> Color.parseColor("#FF6E40")
            "SHADOWSOCKS" -> Color.parseColor("#69F0AE")
            "HYSTERIA2" -> Color.parseColor("#FF5252")
            else -> Color.parseColor("#6C63FF")
        }
    }
}
