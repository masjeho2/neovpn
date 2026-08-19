package com.neovpn.ui

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.neovpn.R
import com.neovpn.dto.ProfileItem
import com.neovpn.dto.SubscriptionItem
import com.neovpn.fmt.ConfigParser
import com.neovpn.handler.MmkvManager

class SubscriptionActivity : AppCompatActivity() {

    private lateinit var rvSubscriptions: RecyclerView
    private lateinit var tvNoSubs: TextView
    private var subAdapter: SubAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        rvSubscriptions = findViewById(R.id.rv_subscriptions)
        tvNoSubs = findViewById(R.id.tv_no_subs)

        subAdapter = SubAdapter(
            mutableListOf(),
            onUpdate = { sub -> updateSubscription(sub) },
            onDelete = { sub -> deleteSubscription(sub) },
            onShowServers = { sub -> showServersForSub(sub) }
        )
        rvSubscriptions.layoutManager = LinearLayoutManager(this)
        rvSubscriptions.adapter = subAdapter

        findViewById<MaterialButton>(R.id.btn_add_sub).setOnClickListener {
            showAddSubDialog()
        }

        findViewById<MaterialButton>(R.id.btn_paste_sub).setOnClickListener {
            importFromClipboard()
        }

        loadSubscriptions()
    }

    private fun loadSubscriptions() {
        val subs = MmkvManager.getSubscriptionList().map { it.second }
        subAdapter?.updateData(subs)
        tvNoSubs.visibility = if (subs.isEmpty()) View.VISIBLE else View.GONE
        rvSubscriptions.visibility = if (subs.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun importFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if (text.isNotEmpty() && text.startsWith("http")) {
                val name = "Sub ${System.currentTimeMillis() % 10000}"
                val sub = SubscriptionItem(remarks = name, url = text)
                MmkvManager.saveSubscription(sub)
                Toast.makeText(this, "Subscription added", Toast.LENGTH_SHORT).show()
                loadSubscriptions()
            } else if (text.isNotEmpty()) {
                // Try to parse as config list
                val configs = ConfigParser.parseBase64Content(text)
                if (configs.isNotEmpty()) {
                    configs.forEach { MmkvManager.addServer(it) }
                    Toast.makeText(this, "Imported ${configs.size} servers", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Invalid content", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "Clipboard empty", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddSubDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_sub, null)
        val etName = view.findViewById<EditText>(R.id.et_sub_name)
        val etUrl = view.findViewById<EditText>(R.id.et_sub_url)

        AlertDialog.Builder(this)
            .setTitle("Add Subscription")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val name = etName.text.toString().trim()
                val url = etUrl.text.toString().trim()
                if (name.isNotEmpty() && url.isNotEmpty()) {
                    val sub = SubscriptionItem(remarks = name, url = url)
                    MmkvManager.saveSubscription(sub)
                    updateSubscription(sub)
                } else {
                    Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateSubscription(sub: SubscriptionItem) {
        Toast.makeText(this, "Updating ${sub.remarks}...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val url = java.net.URL(sub.url)
                val connection = url.openConnection()
                connection.setRequestProperty("User-Agent", "v2rayNG")
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                val content = connection.getInputStream().bufferedReader().readText()

                // Remove old servers from this subscription first
                MmkvManager.removeServersBySubId(sub.guid)

                // Parse and add new servers
                val profiles = ConfigParser.parseBase64Content(content)
                profiles.forEach { profile ->
                    profile.subid = sub.guid
                    MmkvManager.addServer(profile)
                }

                sub.serverCount = profiles.size
                sub.lastUpdate = System.currentTimeMillis()
                MmkvManager.saveSubscription(sub)

                runOnUiThread {
                    Toast.makeText(this, "Updated: ${profiles.size} servers", Toast.LENGTH_SHORT).show()
                    loadSubscriptions()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showServersForSub(sub: SubscriptionItem) {
        val servers = MmkvManager.getServersBySubId(sub.guid)
        if (servers.isEmpty()) {
            Toast.makeText(this, "No servers. Tap Update first.", Toast.LENGTH_SHORT).show()
            return
        }
        val names = servers.map { it.remarks }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("${sub.remarks} (${servers.size} servers)")
            .setItems(names) { _, which ->
                val profile = servers[which]
                MmkvManager.setSelectedServer(profile.guid)
                Toast.makeText(this, "Selected: ${profile.remarks}", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun deleteSubscription(sub: SubscriptionItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Subscription")
            .setMessage("Delete '${sub.remarks}' and its ${sub.serverCount} servers?")
            .setPositiveButton("Delete") { _, _ ->
                MmkvManager.removeServersBySubId(sub.guid)
                MmkvManager.removeSubscription(sub.guid)
                loadSubscriptions()
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    class SubAdapter(
        private val subs: MutableList<SubscriptionItem>,
        private val onUpdate: (SubscriptionItem) -> Unit,
        private val onDelete: (SubscriptionItem) -> Unit,
        private val onShowServers: (SubscriptionItem) -> Unit
    ) : RecyclerView.Adapter<SubAdapter.SubViewHolder>() {

        class SubViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_sub_name)
            val tvUrl: TextView = view.findViewById(R.id.tv_sub_url)
            val tvCount: TextView = view.findViewById(R.id.tv_server_count)
            val tvLastUpdate: TextView = view.findViewById(R.id.tv_last_update)
            val btnUpdate: MaterialButton = view.findViewById(R.id.btn_update_sub)
            val btnDelete: MaterialButton = view.findViewById(R.id.btn_delete_sub)
            val btnServers: MaterialButton = view.findViewById(R.id.btn_servers)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_subscription, parent, false)
            return SubViewHolder(view)
        }

        override fun onBindViewHolder(holder: SubViewHolder, position: Int) {
            val sub = subs[position]
            holder.tvName.text = sub.remarks
            holder.tvUrl.text = sub.url
            holder.tvCount.text = "${sub.serverCount} servers"

            val lastUpdate = if (sub.lastUpdate > 0) {
                val sdf = java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault())
                sdf.format(java.util.Date(sub.lastUpdate))
            } else "Never"
            holder.tvLastUpdate.text = "Updated: $lastUpdate"

            holder.btnUpdate.setOnClickListener { onUpdate(sub) }
            holder.btnDelete.setOnClickListener { onDelete(sub) }
            holder.btnServers.setOnClickListener { onShowServers(sub) }

            // Show servers button only if there are servers
            holder.btnServers.visibility = if (sub.serverCount > 0) View.VISIBLE else View.GONE
        }

        override fun getItemCount(): Int = subs.size

        fun updateData(newSubs: List<SubscriptionItem>) {
            subs.clear()
            subs.addAll(newSubs)
            notifyDataSetChanged()
        }
    }
}
