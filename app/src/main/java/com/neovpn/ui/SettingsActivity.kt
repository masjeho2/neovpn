package com.neovpn.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar
import com.neovpn.R
import com.neovpn.handler.MmkvManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.pref_settings, rootKey)
            loadValues()
        }

        private fun loadValues() {
            // General
            findPreference<CheckBoxPreference>("pref_sniffing")?.isChecked =
                MmkvManager.getSettingBool("pref_sniffing", true)
            findPreference<CheckBoxPreference>("pref_auto_connect_boot")?.isChecked =
                MmkvManager.getSettingBool("pref_auto_connect_boot", false)

            // VPN
            findPreference<CheckBoxPreference>("pref_prefer_ipv6")?.isChecked =
                MmkvManager.getSettingBool("pref_prefer_ipv6", false)
            findPreference<CheckBoxPreference>("pref_bypass_lan")?.isChecked =
                MmkvManager.getSettingBool("pref_bypass_lan", false)
            findPreference<CheckBoxPreference>("pref_local_dns")?.isChecked =
                MmkvManager.getSettingBool("pref_local_dns", true)

            findPreference<EditTextPreference>("pref_vpn_mtu")?.text =
                MmkvManager.getSettingString("pref_vpn_mtu", "1500")
            findPreference<EditTextPreference>("pref_vpn_dns")?.text =
                MmkvManager.getSettingString("pref_vpn_dns", "8.8.8.8")

            // Tunnel
            findPreference<CheckBoxPreference>("pref_use_hev_tunnel")?.isChecked =
                MmkvManager.getSettingBool("pref_use_hev_tunnel", true)
            findPreference<ListPreference>("pref_hev_log_level")?.value =
                MmkvManager.getSettingString("pref_hev_log_level", "warn")

            // Core
            findPreference<CheckBoxPreference>("pref_allow_insecure")?.isChecked =
                MmkvManager.getSettingBool("pref_allow_insecure", true)
            findPreference<EditTextPreference>("pref_socks_port")?.text =
                MmkvManager.getSettingString("pref_socks_port", "10808")
            findPreference<EditTextPreference>("pref_http_port")?.text =
                MmkvManager.getSettingString("pref_http_port", "10809")
            findPreference<EditTextPreference>("pref_remote_dns")?.text =
                MmkvManager.getSettingString("pref_remote_dns", "8.8.8.8")
            findPreference<EditTextPreference>("pref_domestic_dns")?.text =
                MmkvManager.getSettingString("pref_domestic_dns", "localhost")
            findPreference<ListPreference>("pref_core_log_level")?.value =
                MmkvManager.getSettingString("pref_core_log_level", "warning")

            // Subscription
            findPreference<CheckBoxPreference>("pref_auto_update_sub")?.isChecked =
                MmkvManager.getSettingBool("pref_auto_update_sub", false)
            findPreference<EditTextPreference>("pref_auto_update_interval")?.text =
                MmkvManager.getSettingString("pref_auto_update_interval", "1440")

            // UI
            findPreference<CheckBoxPreference>("pref_show_speed")?.isChecked =
                MmkvManager.getSettingBool("pref_show_speed", true)
            findPreference<CheckBoxPreference>("pref_confirm_remove")?.isChecked =
                MmkvManager.getSettingBool("pref_confirm_remove", true)
            findPreference<ListPreference>("pref_theme")?.value =
                MmkvManager.getSettingString("pref_theme", "dark")

            // Advanced
            findPreference<EditTextPreference>("pref_delay_test_url")?.text =
                MmkvManager.getSettingString("pref_delay_test_url", "http://www.gstatic.com/generate_204")

            // Listeners
            setupListeners()
        }

        private fun setupListeners() {
            findPreference<CheckBoxPreference>("pref_allow_insecure")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_allow_insecure", v as Boolean); true
            }
            // Save on change
            findPreference<CheckBoxPreference>("pref_sniffing")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_sniffing", v as Boolean); true
            }
            findPreference<CheckBoxPreference>("pref_auto_connect_boot")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_auto_connect_boot", v as Boolean); true
            }
            findPreference<CheckBoxPreference>("pref_prefer_ipv6")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_prefer_ipv6", v as Boolean); true
            }
            findPreference<CheckBoxPreference>("pref_bypass_lan")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_bypass_lan", v as Boolean); true
            }
            findPreference<CheckBoxPreference>("pref_local_dns")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_local_dns", v as Boolean); true
            }
            findPreference<EditTextPreference>("pref_vpn_mtu")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_vpn_mtu", v as String); true
            }
            findPreference<EditTextPreference>("pref_vpn_dns")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_vpn_dns", v as String); true
            }
            findPreference<CheckBoxPreference>("pref_use_hev_tunnel")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_use_hev_tunnel", v as Boolean); true
            }
            findPreference<ListPreference>("pref_hev_log_level")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_hev_log_level", v as String); true
            }
            findPreference<EditTextPreference>("pref_socks_port")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_socks_port", v as String); true
            }
            findPreference<EditTextPreference>("pref_http_port")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_http_port", v as String); true
            }
            findPreference<EditTextPreference>("pref_remote_dns")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_remote_dns", v as String); true
            }
            findPreference<EditTextPreference>("pref_domestic_dns")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_domestic_dns", v as String); true
            }
            findPreference<ListPreference>("pref_core_log_level")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_core_log_level", v as String); true
            }
            findPreference<CheckBoxPreference>("pref_auto_update_sub")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_auto_update_sub", v as Boolean); true
            }
            findPreference<EditTextPreference>("pref_auto_update_interval")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_auto_update_interval", v as String); true
            }
            findPreference<CheckBoxPreference>("pref_show_speed")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_show_speed", v as Boolean); true
            }
            findPreference<CheckBoxPreference>("pref_confirm_remove")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_confirm_remove", v as Boolean); true
            }
            findPreference<ListPreference>("pref_theme")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_theme", v as String); true
            }
            findPreference<EditTextPreference>("pref_delay_test_url")?.setOnPreferenceChangeListener { _, v ->
                MmkvManager.setSetting("pref_delay_test_url", v as String); true
            }
        }
    }
}
