package com.neovpn.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.neovpn.R
import com.neovpn.dto.ProfileItem
import com.neovpn.fmt.ConfigParser
import com.neovpn.handler.MmkvManager
import com.neovpn.service.NeoVpnService

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var btnConnect: ImageButton
    private lateinit var connectButtonBg: View
    private lateinit var connectPulse: View
    private lateinit var tvStatus: TextView
    private lateinit var tvServerName: TextView
    private lateinit var tvUpload: TextView
    private lateinit var tvDownload: TextView
    private lateinit var rvServers: RecyclerView
    private lateinit var tvNoServers: TextView
    private lateinit var navView: NavigationView

    private var serverAdapter: ServerAdapter? = null
    private val handler = Handler(Looper.getMainLooper())
    private var vpnState = "disconnected"
    private var speedMonitor: com.neovpn.util.SpeedMonitor? = null

    private val vpnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            vpnState = intent.getStringExtra("state") ?: "disconnected"
            handler.post {
                updateUI()
                // Toggle periodic quota refresh sesuai VPN state
                if (vpnState == "connected") {
                    serverAdapter?.startPeriodicQuotaRefresh()
                } else {
                    serverAdapter?.stopPeriodicQuotaRefresh()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupToolbar()
        setupDrawer()
        setupRecyclerView()
        setupButtons()

        // Register VPN state receiver
        val filter = IntentFilter("com.neovpn.VPN_STATE_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(vpnReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(vpnReceiver, filter)
        }

        // Sync state from service
        vpnState = if (NeoVpnService.isRunning) "connected" else "disconnected"
        updateUI()
        loadServers()

        // Start periodic quota refresh jika VPN sudah connected saat boot
        if (vpnState == "connected") {
            serverAdapter?.startPeriodicQuotaRefresh()
        }
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        btnConnect = findViewById(R.id.btn_connect)
        connectButtonBg = findViewById(R.id.connect_button_bg)
        connectPulse = findViewById(R.id.connect_pulse)
        tvStatus = findViewById(R.id.tv_status)
        tvServerName = findViewById(R.id.tv_server_name)
        tvUpload = findViewById(R.id.tv_upload)
        tvDownload = findViewById(R.id.tv_download)
        rvServers = findViewById(R.id.rv_servers)
        tvNoServers = findViewById(R.id.tv_no_servers)
        navView = findViewById(R.id.nav_view)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val toggle = androidx.appcompat.app.ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        toolbar.setNavigationOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }
    }

    private fun setupDrawer() {
        navView.getHeaderView(0)?.findViewById<TextView>(R.id.tv_nav_version)?.text =
            "v${getAppVersion()}"
        navView.setCheckedItem(R.id.nav_home)
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    navView.setCheckedItem(R.id.nav_home)
                    drawerLayout.closeDrawer(GravityCompat.START); true
                }
                R.id.nav_servers -> {
                    navView.setCheckedItem(R.id.nav_servers)
                    rvServers.post { rvServers.parent?.requestChildFocus(rvServers, rvServers) }
                    drawerLayout.closeDrawer(GravityCompat.START); true
                }
                R.id.nav_subscription -> {
                    startActivity(Intent(this, SubscriptionActivity::class.java))
                    drawerLayout.closeDrawer(GravityCompat.START); true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    drawerLayout.closeDrawer(GravityCompat.START); true
                }
                R.id.nav_import -> {
                    showImportDialog()
                    drawerLayout.closeDrawer(GravityCompat.START); true
                }
                R.id.nav_log -> {
                    startActivity(Intent(this, ConnectionLogActivity::class.java))
                    drawerLayout.closeDrawer(GravityCompat.START); true
                }
                R.id.nav_about -> {
                    showAboutDialog()
                    drawerLayout.closeDrawer(GravityCompat.START); true
                }
                R.id.nav_update -> {
                    Toast.makeText(this, "You're on v${getAppVersion()}", Toast.LENGTH_SHORT).show()
                    drawerLayout.closeDrawer(GravityCompat.START); true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        serverAdapter = ServerAdapter(
            mutableListOf(),
            onServerClick = { profile -> selectServer(profile) },
            onMoreClick = { profile -> showServerOptions(profile) },
            onRefreshGroup = { subGuid -> refreshSubscription(subGuid) },
            scope = this
        )
        rvServers.layoutManager = LinearLayoutManager(this)
        rvServers.adapter = serverAdapter

        // Start periodic quota refresh jika VPN sudah connected saat boot
        if (vpnState == "connected") {
            serverAdapter?.startPeriodicQuotaRefresh()
        }
    }

    private fun refreshSubscription(subGuid: String) {
        val sub = MmkvManager.getSubscriptionList().find { it.first == subGuid }?.second ?: return
        Toast.makeText(this, "Updating ${sub.remarks}...", Toast.LENGTH_SHORT).show()

        // Force refresh quota juga (invalidate cache + fetch baru)
        serverAdapter?.forceRefreshQuota(subGuid)

        Thread {
            try {
                val conn = java.net.URL(sub.url).openConnection()
                conn.setRequestProperty("User-Agent", "v2rayNG")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                val content = conn.getInputStream().bufferedReader().readText()

                MmkvManager.removeServersBySubId(subGuid)

                val profiles = ConfigParser.parseBase64Content(content)
                profiles.forEach { it.subid = subGuid }
                sub.serverCount = profiles.size
                sub.lastUpdate = System.currentTimeMillis()
                MmkvManager.saveSubscription(sub)
                profiles.forEach { MmkvManager.addServer(it) }

                runOnUiThread {
                    loadServers()
                    Toast.makeText(this, "Updated: ${profiles.size} servers", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun setupButtons() {
        btnConnect.setOnClickListener {
            if (NeoVpnService.isRunning) {
                stopVpn()
            } else {
                startVpn()
            }
        }

        findViewById<MaterialButton>(R.id.btn_add_server).setOnClickListener {
            startActivity(Intent(this, AddServerActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btn_scan_qr).setOnClickListener {
            val intent = Intent(this, ScannerActivity::class.java)
            startActivityForResult(intent, 1001)
        }

        findViewById<MaterialButton>(R.id.btn_paste).setOnClickListener {
            importFromClipboard()
        }

        findViewById<MaterialButton>(R.id.btn_connection_log).setOnClickListener {
            startActivity(Intent(this, ConnectionLogActivity::class.java))
        }

        applyPress(btnConnect)
        applyPress(connectButtonBg)
        applyPress(findViewById(R.id.btn_add_server))
        applyPress(findViewById(R.id.btn_scan_qr))
        applyPress(findViewById(R.id.btn_paste))
        applyPress(findViewById(R.id.btn_connection_log))
    }

    private fun applyPress(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }
            false
        }
    }

    private fun startVpn() {
        val profile = MmkvManager.getSelectedServer()
        if (profile == null) {
            Toast.makeText(this, "Select a server first", Toast.LENGTH_SHORT).show()
            return
        }

        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, 1000)
        } else {
            startVpnService()
        }
    }

    private fun startSpeedMonitor() {
        if (!com.neovpn.handler.SettingsManager.isShowSpeed()) return
        speedMonitor?.stop()
        speedMonitor = com.neovpn.util.SpeedMonitor { up, down, totalUp, totalDown ->
            handler.post {
                tvUpload.text = com.neovpn.util.SpeedMonitor.formatSpeed(up)
                tvDownload.text = com.neovpn.util.SpeedMonitor.formatSpeed(down)
            }
        }
        speedMonitor?.start()
    }

    private fun stopSpeedMonitor() {
        speedMonitor?.stop()
        speedMonitor = null
        tvUpload.text = "0 B/s"
        tvDownload.text = "0 B/s"
    }

    private fun stopVpn() {
        val intent = Intent(this, NeoVpnService::class.java)
        intent.action = NeoVpnService.ACTION_STOP
        startService(intent)
    }

    private fun startVpnService() {
        vpnState = "connecting"
        updateUI()
        val intent = Intent(this, NeoVpnService::class.java)
        intent.action = NeoVpnService.ACTION_START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun selectServer(profile: ProfileItem) {
        MmkvManager.setSelectedServer(profile.guid)
        loadServers()
        updateUI()
    }

    private fun showServerOptions(profile: ProfileItem) {
        val options = arrayOf("Test Latency", "Edit", "Share", "Copy Config", "Delete")
        AlertDialog.Builder(this)
            .setTitle(profile.remarks)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> testLatency(profile)
                    1 -> editServer(profile)
                    2 -> shareServer(profile)
                    3 -> copyConfig(profile)
                    4 -> deleteServer(profile)
                }
            }
            .show()
    }

    private fun testLatency(profile: ProfileItem) {
        Toast.makeText(this, "Testing latency...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val url = com.neovpn.handler.SettingsManager.getDelayTestUrl()
                val latency = com.neovpn.handler.XrayController.measureDelay(url)
                runOnUiThread {
                    profile.lastLatency = latency.toInt()
                    MmkvManager.updateServer(profile)
                    loadServers()
                    Toast.makeText(this, "Latency: ${latency}ms", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Test failed", Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun editServer(profile: ProfileItem) {
        val intent = Intent(this, AddServerActivity::class.java)
        intent.putExtra("guid", profile.guid)
        startActivity(intent)
    }

    private fun shareServer(profile: ProfileItem) {
        val uri = profile.toUri()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, uri)
        }
        startActivity(Intent.createChooser(intent, "Share Server"))
    }

    private fun copyConfig(profile: ProfileItem) {
        val uri = profile.toUri()
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("NeoVPN Config", uri)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    private fun deleteServer(profile: ProfileItem) {
        AlertDialog.Builder(this)
            .setTitle("Delete Server")
            .setMessage("Delete ${profile.remarks}?")
            .setPositiveButton("Delete") { _, _ ->
                MmkvManager.removeServer(profile.guid)
                loadServers()
                updateUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importFromClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if (text.isNotEmpty()) {
                // Check if it's a subscription URL (http/https with path segments)
                if (text.trim().startsWith("http") && !ConfigParser.isConfigUri(text.trim())) {
                    importSubscriptionFromUrl(text.trim())
                } else {
                    // Try as server config
                    val profile = ConfigParser.parse(text)
                    if (profile != null) {
                        MmkvManager.addServer(profile)
                        loadServers()
                        updateUI()
                        Toast.makeText(this, "Imported: ${profile.remarks}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Invalid config", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(this, "Clipboard empty", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importSubscriptionFromUrl(url: String) {
        // Extract name from last path segment
        val name = try {
            val path = java.net.URL(url).path
            path.substringAfterLast('/').ifEmpty { "Sub ${System.currentTimeMillis() % 10000}" }
        } catch (e: Exception) {
            "Sub ${System.currentTimeMillis() % 10000}"
        }

        Toast.makeText(this, "Adding subscription: $name", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val conn = java.net.URL(url).openConnection()
                conn.setRequestProperty("User-Agent", "v2rayNG")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                val content = conn.getInputStream().bufferedReader().readText()

                val sub = com.neovpn.dto.SubscriptionItem(remarks = name, url = url)

                // Save subscription FIRST to generate GUID
                MmkvManager.saveSubscription(sub)

                val profiles = ConfigParser.parseBase64Content(content)
                profiles.forEach { it.subid = sub.guid }
                sub.serverCount = profiles.size
                sub.lastUpdate = System.currentTimeMillis()

                MmkvManager.saveSubscription(sub) // Update with server count
                profiles.forEach { MmkvManager.addServer(it) }

                runOnUiThread {
                    loadServers()
                    updateUI()
                    Toast.makeText(this, "Imported ${profiles.size} servers from $name", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showImportDialog() {
        val options = arrayOf("Paste from Clipboard", "Import Subscription")
        AlertDialog.Builder(this)
            .setTitle("Import Config")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> importFromClipboard()
                    1 -> startActivity(Intent(this, SubscriptionActivity::class.java))
                }
            }
            .show()
    }

    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("NeoVPN")
            .setMessage(getString(R.string.about_message, getAppVersion()))
            .setPositiveButton("OK", null)
            .show()
    }

    private fun getAppVersion(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.4"
        } catch (_: Exception) {
            "1.0.4"
        }
    }

    private fun loadServers() {
        val servers = MmkvManager.getServerList()
        val profiles = servers.mapNotNull { MmkvManager.decodeServerConfig(it) }
        serverAdapter?.updateData(profiles)
        tvNoServers.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
        rvServers.visibility = if (profiles.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateUI() {
        val selectedServer = MmkvManager.getSelectedServer()

        when (vpnState) {
            "connected" -> {
                tvStatus.text = getString(R.string.connected)
                tvStatus.setTextColor(getColor(R.color.status_connected))
                tvStatus.setBackgroundResource(R.drawable.status_chip_bg)
                tvStatus.background.mutate().setTint(getColor(R.color.status_chip_on))
                connectButtonBg.setBackgroundResource(R.drawable.circle_button_bg_connected)
                tvServerName.text = selectedServer?.remarks ?: "Unknown"
                startConnectPulse()
                if (speedMonitor == null) startSpeedMonitor()
            }
            "connecting" -> {
                tvStatus.text = getString(R.string.connecting)
                tvStatus.setTextColor(getColor(R.color.status_connecting))
                tvStatus.setBackgroundResource(R.drawable.status_chip_bg)
                tvStatus.background.mutate().setTint(getColor(R.color.status_chip_wait))
                connectButtonBg.setBackgroundResource(R.drawable.circle_button_bg_connecting)
                tvServerName.text = selectedServer?.remarks ?: "Unknown"
                stopConnectPulse()
            }
            else -> {
                tvStatus.text = getString(R.string.disconnected)
                tvStatus.setTextColor(getColor(R.color.status_disconnected))
                tvStatus.setBackgroundResource(R.drawable.status_chip_bg)
                tvStatus.background.mutate().setTint(getColor(R.color.status_chip_off))
                connectButtonBg.setBackgroundResource(R.drawable.circle_button_bg)
                tvServerName.text = selectedServer?.remarks ?: "No server selected"
                stopConnectPulse()
                stopSpeedMonitor()
            }
        }
    }

    private fun startConnectPulse() {
        connectPulse.visibility = View.VISIBLE
        if (connectPulse.animation == null) {
            connectPulse.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse_ring))
        }
    }

    private fun stopConnectPulse() {
        connectPulse.clearAnimation()
        connectPulse.visibility = View.GONE
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java)); true
            }
            R.id.action_subscription -> {
                startActivity(Intent(this, SubscriptionActivity::class.java)); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            1000 -> {
                if (resultCode == Activity.RESULT_OK) {
                    startVpnService()
                } else {
                    Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            1001 -> {
                if (resultCode == Activity.RESULT_OK) {
                    val config = data?.getStringExtra("config") ?: ""
                    val trimmed = config.trim()
                    if (trimmed.isNotEmpty()) {
                        // Check if it's a subscription URL
                        if (trimmed.startsWith("http") && !ConfigParser.isConfigUri(trimmed)) {
                            importSubscriptionFromUrl(trimmed)
                        } else {
                            // Try as server config
                            val profile = ConfigParser.parse(trimmed)
                            if (profile != null) {
                                MmkvManager.addServer(profile)
                                loadServers()
                                updateUI()
                                Toast.makeText(this, "Imported: ${profile.remarks}", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this, "Invalid config", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Sync state from service
        vpnState = if (NeoVpnService.isRunning) "connected" else "disconnected"
        loadServers()
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        serverAdapter?.stopPeriodicQuotaRefresh()
        stopConnectPulse()
        try { unregisterReceiver(vpnReceiver) } catch (_: Exception) {}
    }
}
