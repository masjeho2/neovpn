package com.neovpn.ui

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.neovpn.R
import com.neovpn.dto.ProfileItem
import com.neovpn.handler.MmkvManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ServerAdapter(
    private val items: MutableList<Any>,
    private val onServerClick: (ProfileItem) -> Unit,
    private val onMoreClick: (ProfileItem) -> Unit,
    private val onGroupClick: (String) -> Unit = {},
    private val onRefreshGroup: (String) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_SERVER = 1
    }

    private val expandedGroups = mutableSetOf<String>()

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHeader: TextView = view.findViewById(R.id.tv_section_header)
        val tvTimestamp: TextView = view.findViewById(R.id.tv_sub_timestamp)
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

                    val lastUpdate = if (sub.second.lastUpdate > 0) {
                        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                        sdf.format(Date(sub.second.lastUpdate))
                    } else "Never"
                    holder.tvTimestamp.text = "Updated: $lastUpdate"

                    holder.btnRefresh.setOnClickListener { onRefreshGroup(sub.first) }
                    holder.tvHeader.setOnClickListener {
                        val intent = Intent(holder.itemView.context, EditSubscriptionActivity::class.java)
                        intent.putExtra("sub_guid", sub.first)
                        holder.itemView.context.startActivity(intent)
                    }
                } else {
                    // Manual servers group
                    holder.btnRefresh.visibility = View.GONE
                    holder.tvTimestamp.visibility = View.GONE
                }

                // Expand/collapse
                val isExpanded = expandedGroups.contains(groupName)
                holder.btnExpand.rotation = if (isExpanded) 0f else 270f
                holder.btnExpand.setOnClickListener {
                    if (isExpanded) expandedGroups.remove(groupName) else expandedGroups.add(groupName)
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

                // Highlight selected/active server
                val selectedGuid = MmkvManager.getSelectedServer()?.guid
                val isActive = server.guid == selectedGuid
                holder.tvActiveBadge.visibility = if (isActive) View.VISIBLE else View.GONE
                holder.viewActiveBar.visibility = if (isActive) View.VISIBLE else View.GONE

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
