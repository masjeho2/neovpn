package com.neovpn.ui

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.neovpn.R
import com.neovpn.dto.EConfigType
import com.neovpn.dto.ProfileItem
import com.neovpn.handler.MmkvManager

class AddServerActivity : AppCompatActivity() {

    private lateinit var etServerName: EditText
    private lateinit var etAddress: EditText
    private lateinit var etPort: EditText
    private lateinit var spProtocol: Spinner

    // VMess
    private lateinit var layoutVmess: LinearLayout
    private lateinit var etId: EditText
    private lateinit var spSecurity: Spinner

    // VLESS
    private lateinit var layoutVless: LinearLayout
    private lateinit var etVlessId: EditText
    private lateinit var etVlessFlow: EditText

    // Trojan
    private lateinit var layoutTrojan: LinearLayout
    private lateinit var etTrojanPassword: EditText

    // SS
    private lateinit var layoutSS: LinearLayout
    private lateinit var etSSPassword: EditText
    private lateinit var spSSMethod: Spinner

    // Transport
    private lateinit var layoutTransport: LinearLayout
    private lateinit var spNetwork: Spinner
    private lateinit var etHost: EditText
    private lateinit var etPath: EditText
    private lateinit var layoutGrpcMode: LinearLayout
    private lateinit var spGrpcMode: Spinner

    // TLS
    private lateinit var layoutTls: LinearLayout
    private lateinit var etSni: EditText
    private lateinit var spFingerprint: Spinner
    private lateinit var spAlpn: Spinner
    private lateinit var spAllowInsecure: Spinner

    private var editingGuid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_server)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        initViews()
        setupSpinners()
        setupListeners()

        editingGuid = intent.getStringExtra("guid")
        if (editingGuid != null) loadServer(editingGuid!!)

        findViewById<MaterialButton>(R.id.btn_save).setOnClickListener { saveServer() }
    }

    private fun initViews() {
        etServerName = findViewById(R.id.et_server_name)
        etAddress = findViewById(R.id.et_address)
        etPort = findViewById(R.id.et_port)
        spProtocol = findViewById(R.id.sp_protocol)
        layoutVmess = findViewById(R.id.layout_vmess)
        etId = findViewById(R.id.et_id)
        spSecurity = findViewById(R.id.sp_security)
        layoutVless = findViewById(R.id.layout_vless)
        etVlessId = findViewById(R.id.et_vless_id)
        etVlessFlow = findViewById(R.id.et_vless_flow)
        layoutTrojan = findViewById(R.id.layout_trojan)
        etTrojanPassword = findViewById(R.id.et_trojan_password)
        layoutSS = findViewById(R.id.layout_ss)
        etSSPassword = findViewById(R.id.et_ss_password)
        spSSMethod = findViewById(R.id.sp_ss_method)
        layoutTransport = findViewById(R.id.layout_transport)
        spNetwork = findViewById(R.id.sp_network)
        etHost = findViewById(R.id.et_host)
        etPath = findViewById(R.id.et_path)
        layoutGrpcMode = findViewById(R.id.layout_grpc_mode)
        spGrpcMode = findViewById(R.id.sp_grpc_mode)
        layoutTls = findViewById(R.id.layout_tls)
        etSni = findViewById(R.id.et_sni)
        spFingerprint = findViewById(R.id.sp_fingerprint)
        spAlpn = findViewById(R.id.sp_alpn)
        spAllowInsecure = findViewById(R.id.sp_allow_insecure)
    }

    private fun setupSpinners() {
        spProtocol.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("VMess", "VLESS", "Trojan", "Shadowsocks"))
        spSecurity.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("auto", "none", "aes-128-gcm", "chacha20-poly1305", "zero"))
        spNetwork.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("tcp", "ws", "grpc", "h2"))
        spFingerprint.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("", "chrome", "firefox", "safari", "edge", "random", "ios", "android"))
        spAlpn.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("", "h2", "http/1.1", "h2,http/1.1"))
        spAllowInsecure.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("No", "Yes"))
        spGrpcMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("gun", "multi"))
        spSSMethod.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf("aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305", "2022-blake3-aes-128-gcm"))
    }

    private fun setupListeners() {
        spProtocol.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                layoutVmess.visibility = if (pos == 0) View.VISIBLE else View.GONE
                layoutVless.visibility = if (pos == 1) View.VISIBLE else View.GONE
                layoutTrojan.visibility = if (pos == 2) View.VISIBLE else View.GONE
                layoutSS.visibility = if (pos == 3) View.VISIBLE else View.GONE
                layoutTransport.visibility = if (pos < 3) View.VISIBLE else View.GONE
                layoutTls.visibility = if (pos < 3) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spNetwork.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val network = parent?.getItemAtPosition(pos).toString()
                layoutGrpcMode.visibility = if (network == "grpc") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadServer(guid: String) {
        val p = MmkvManager.decodeServerConfig(guid) ?: return
        etServerName.setText(p.remarks)
        etAddress.setText(p.address)
        etPort.setText(p.port.toString())
        when (p.configType) {
            EConfigType.VMESS -> { spProtocol.setSelection(0); etId.setText(p.id); setSpinner(spSecurity, p.security) }
            EConfigType.VLESS -> { spProtocol.setSelection(1); etVlessId.setText(p.id); etVlessFlow.setText(p.flow) }
            EConfigType.TROJAN -> { spProtocol.setSelection(2); etTrojanPassword.setText(p.id) }
            EConfigType.SHADOWSOCKS -> { spProtocol.setSelection(3); etSSPassword.setText(p.id); setSpinner(spSSMethod, p.protocol) }
            else -> {}
        }
        setSpinner(spNetwork, p.network)
        etHost.setText(p.host)
        etPath.setText(p.path)
        etSni.setText(p.sni)
        setSpinner(spFingerprint, p.fingerprint)
        setSpinner(spAlpn, p.alpn)
        setSpinner(spAllowInsecure, if (p.allowInsecure) "Yes" else "No")
    }

    private fun setSpinner(spinner: Spinner, value: String) {
        for (i in 0 until (spinner.adapter?.count ?: 0)) {
            if (spinner.adapter.getItem(i).toString() == value) { spinner.setSelection(i); return }
        }
    }

    private fun saveServer() {
        val name = etServerName.text.toString().trim()
        val address = etAddress.text.toString().trim()
        val port = etPort.text.toString().trim().toIntOrNull() ?: 443
        if (name.isEmpty() || address.isEmpty()) { Toast.makeText(this, "Fill name & address", Toast.LENGTH_SHORT).show(); return }

        val profile = ProfileItem(guid = editingGuid ?: "", remarks = name, address = address, port = port)

        when (spProtocol.selectedItemPosition) {
            0 -> {
                val id = etId.text.toString().trim()
                if (id.isEmpty()) { Toast.makeText(this, "UUID required", Toast.LENGTH_SHORT).show(); return }
                profile.configType = EConfigType.VMESS; profile.id = id; profile.security = spSecurity.selectedItem.toString()
            }
            1 -> {
                val id = etVlessId.text.toString().trim()
                if (id.isEmpty()) { Toast.makeText(this, "UUID required", Toast.LENGTH_SHORT).show(); return }
                profile.configType = EConfigType.VLESS; profile.id = id; profile.flow = etVlessFlow.text.toString().trim()
            }
            2 -> {
                val pw = etTrojanPassword.text.toString().trim()
                if (pw.isEmpty()) { Toast.makeText(this, "Password required", Toast.LENGTH_SHORT).show(); return }
                profile.configType = EConfigType.TROJAN; profile.id = pw
            }
            3 -> {
                val pw = etSSPassword.text.toString().trim()
                if (pw.isEmpty()) { Toast.makeText(this, "Password required", Toast.LENGTH_SHORT).show(); return }
                profile.configType = EConfigType.SHADOWSOCKS; profile.id = pw; profile.protocol = spSSMethod.selectedItem.toString()
            }
        }

        profile.network = spNetwork.selectedItem.toString()
        profile.host = etHost.text.toString().trim()
        profile.path = etPath.text.toString().trim()
        profile.mode = spGrpcMode.selectedItem.toString()
        profile.tls = "tls"
        profile.sni = etSni.text.toString().trim()
        profile.fingerprint = spFingerprint.selectedItem.toString()
        profile.alpn = spAlpn.selectedItem.toString()
        profile.allowInsecure = spAllowInsecure.selectedItem.toString() == "Yes"

        if (editingGuid != null) MmkvManager.updateServer(profile) else MmkvManager.addServer(profile)
        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        finish()
    }
}
