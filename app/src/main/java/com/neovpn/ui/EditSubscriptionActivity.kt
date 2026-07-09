package com.neovpn.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.neovpn.R
import com.neovpn.dto.ProfileItem
import com.neovpn.dto.SubscriptionItem
import com.neovpn.fmt.ConfigParser
import com.neovpn.handler.MmkvManager

class EditSubscriptionActivity : AppCompatActivity() {

    private lateinit var etRemarks: EditText
    private lateinit var etUrl: EditText
    private lateinit var switchAutoUpdate: Switch
    private lateinit var rvConfigs: RecyclerView
    private lateinit var tvConfigCount: TextView
    private var subGuid: String = ""
    private var configs: MutableList<ProfileItem> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_subscription)

        subGuid = intent.getStringExtra("sub_guid") ?: ""

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_save -> { saveSubscription(); true }
                else -> false
            }
        }

        etRemarks = findViewById(R.id.et_remarks)
        etUrl = findViewById(R.id.et_url)
        switchAutoUpdate = findViewById(R.id.switch_auto_update)
        rvConfigs = findViewById(R.id.rv_configs)
        tvConfigCount = findViewById(R.id.tv_config_count)

        rvConfigs.layoutManager = LinearLayoutManager(this)
        loadSubscription()
    }

    private fun loadSubscription() {
        if (subGuid.isEmpty()) return

        val sub = MmkvManager.getSubscriptionList().find { it.first == subGuid }?.second ?: return

        etRemarks.setText(sub.remarks)
        etUrl.setText(sub.url)
        switchAutoUpdate.isChecked = sub.autoUpdate

        // Load servers for this subscription
        configs = MmkvManager.getServersBySubId(subGuid).toMutableList()
        tvConfigCount.text = "${configs.size} CONFIGURATIONS"

        val adapter = ConfigListAdapter(configs)
        rvConfigs.adapter = adapter
    }

    private fun saveSubscription() {
        val name = etRemarks.text.toString().trim()
        val url = etUrl.text.toString().trim()

        if (name.isEmpty() || url.isEmpty()) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val sub = SubscriptionItem(
            guid = subGuid,
            remarks = name,
            url = url,
            autoUpdate = switchAutoUpdate.isChecked,
            serverCount = configs.size,
            lastUpdate = System.currentTimeMillis()
        )
        MmkvManager.saveSubscription(sub)

        // Update sub name in servers
        configs.forEach { profile ->
            profile.subid = subGuid
            MmkvManager.updateServer(profile)
        }

        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    class ConfigListAdapter(private val items: List<ProfileItem>) :
        RecyclerView.Adapter<ConfigListAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_config_name)
            val tvType: TextView = view.findViewById(R.id.tv_config_type)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_config_list, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = item.remarks
            holder.tvType.text = item.configType.desc
        }

        override fun getItemCount(): Int = items.size
    }
}
