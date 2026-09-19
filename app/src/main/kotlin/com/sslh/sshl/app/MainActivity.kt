package com.sslh.sshl.app

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.textfield.TextInputEditText
import com.sslh.sshl.app.databinding.ActivityMainBinding
import com.sslh.sshl.app.model.TunnelConfig
import com.sslh.sshl.app.model.TunnelStatus
import com.sslh.sshl.app.model.TunnelType
import com.sslh.sshl.app.service.HttpKuVpnService
import com.sslh.sshl.app.service.PayloadGenerator
import com.sslh.sshl.app.service.PayloadGeneratorOptions
import com.sslh.sshl.app.service.TunnelEngine

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val tunnelEngine = TunnelEngine.instance
    private val PREFS_NAME = "httpku_prefs"
    private val KEY_CONFIG = "tunnel_config"
    private val KEY_DARK_MODE = "is_dark_mode"

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnAndEngine()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSavedConfig()
        setupUI()

        tunnelEngine.listener = {
            runOnUiThread {
                updateUIFromEngine()
            }
        }
    }

    private fun loadSavedConfig() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CONFIG, null)
        val config = if (!json.isNullOrEmpty()) {
            TunnelConfig.fromJson(json)
        } else {
            TunnelConfig()
        }
        tunnelEngine.updateConfig(config)
    }

    private fun saveConfig() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CONFIG, tunnelEngine.config.toJson()).apply()
    }

    private fun setupUI() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false)
        binding.switchDarkMode.isChecked = isDarkMode

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        val config = tunnelEngine.config
        binding.etRemoteAddr.setText(config.remoteAddr)
        binding.etRemotePort.setText(config.remotePort.toString())
        binding.etRemoteUsername.setText(config.remoteUsername)
        binding.etRemotePassword.setText(config.remotePassword)

        binding.etHttpAddr.setText(config.httpAddr)
        binding.etHttpPort.setText(config.httpPort.toString())
        binding.etDnsServer.setText(config.dnsServer)
        binding.etCustomResponse.setText(config.customHttpResponse)
        binding.etPayload.setText(config.payload)

        binding.switchProxyAuth.isChecked = config.proxyAuthorization
        binding.switchReplaceResponse.isChecked = config.replaceHttpResponse
        binding.switchCustomPayload.isChecked = config.customPayload
        binding.switchDetectIp.isChecked = config.detectIpv4

        setTunnelTypeRadio(config.type)

        // Text Watchers
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateEngineConfigFromFields()
            }
        }

        binding.etRemoteAddr.addTextChangedListener(textWatcher)
        binding.etRemotePort.addTextChangedListener(textWatcher)
        binding.etRemoteUsername.addTextChangedListener(textWatcher)
        binding.etRemotePassword.addTextChangedListener(textWatcher)
        binding.etHttpAddr.addTextChangedListener(textWatcher)
        binding.etHttpPort.addTextChangedListener(textWatcher)
        binding.etDnsServer.addTextChangedListener(textWatcher)
        binding.etCustomResponse.addTextChangedListener(textWatcher)
        binding.etPayload.addTextChangedListener(textWatcher)

        binding.rgTunnelType.setOnCheckedChangeListener { _, checkedId ->
            val type = when (checkedId) {
                binding.rbDirect.id -> TunnelType.DIRECT
                binding.rbHttp.id -> TunnelType.HTTP
                binding.rbSsl.id -> TunnelType.SSL
                binding.rbSocks.id -> TunnelType.SOCKS
                binding.rbDns.id -> TunnelType.DNS
                binding.rbHaproxy.id -> TunnelType.HAPROXY
                else -> TunnelType.HTTP
            }
            tunnelEngine.updateConfig(tunnelEngine.config.copy(type = type))
            saveConfig()
            updateFieldVisibilities(type)
        }

        binding.switchProxyAuth.setOnCheckedChangeListener { _, isChecked ->
            tunnelEngine.updateConfig(tunnelEngine.config.copy(proxyAuthorization = isChecked))
            saveConfig()
        }

        binding.switchReplaceResponse.setOnCheckedChangeListener { _, isChecked ->
            tunnelEngine.updateConfig(tunnelEngine.config.copy(replaceHttpResponse = isChecked))
            saveConfig()
            updateFieldVisibilities(tunnelEngine.config.type)
        }

        binding.switchCustomPayload.setOnCheckedChangeListener { _, isChecked ->
            tunnelEngine.updateConfig(tunnelEngine.config.copy(customPayload = isChecked))
            saveConfig()
            updateFieldVisibilities(tunnelEngine.config.type)
        }

        binding.switchDetectIp.setOnCheckedChangeListener { _, isChecked ->
            tunnelEngine.updateConfig(tunnelEngine.config.copy(detectIpv4 = isChecked))
            if (isChecked) {
                tunnelEngine.refreshDetectedIp()
            }
            saveConfig()
        }

        binding.btnGenerator1.setOnClickListener { showPayloadGeneratorDialog() }
        binding.btnGenerator2.setOnClickListener { showPayloadGeneratorDialog() }
        binding.btnViewLogs.setOnClickListener { showLogsDialog() }

        binding.btnImportConfig.setOnClickListener { showImportConfigDialog() }
        binding.btnExportConfig.setOnClickListener { showExportConfigDialog() }
        binding.btnPingTest.setOnClickListener { runPingTest() }

        binding.btnStartStop.setOnClickListener {
            if (tunnelEngine.isConnected || tunnelEngine.isConnecting) {
                stopVpnAndEngine()
            } else {
                prepareAndStartVpn()
            }
        }

        updateUIFromEngine()
    }

    private fun showImportConfigDialog() {
        val etInput = EditText(this)
        etInput.hint = "Paste Config JSON here"
        AlertDialog.Builder(this)
            .setTitle("Import Config")
            .setView(etInput)
            .setPositiveButton("Import") { dialog, _ ->
                val json = etInput.text.toString()
                if (tunnelEngine.importConfigJson(json)) {
                    saveConfig()
                    setupUIFieldsFromConfig()
                    Toast.makeText(this, "Config imported successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to import config", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showExportConfigDialog() {
        val json = tunnelEngine.exportConfigJson()
        val etOutput = EditText(this)
        etOutput.setText(json)
        AlertDialog.Builder(this)
            .setTitle("Export Config JSON")
            .setView(etOutput)
            .setPositiveButton("Copy") { dialog, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("HttpKu Config", json)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Config copied to clipboard", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun runPingTest() {
        Toast.makeText(this, "Pinging server...", Toast.LENGTH_SHORT).show()
        tunnelEngine.measurePing(binding.etRemoteAddr.text.toString(), binding.etRemotePort.text.toString().toIntOrNull() ?: 80) { ms ->
            runOnUiThread {
                if (ms != null) {
                    Toast.makeText(this, "Ping result: ${ms}ms", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Ping failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupUIFieldsFromConfig() {
        val config = tunnelEngine.config
        binding.etRemoteAddr.setText(config.remoteAddr)
        binding.etRemotePort.setText(config.remotePort.toString())
        binding.etRemoteUsername.setText(config.remoteUsername)
        binding.etRemotePassword.setText(config.remotePassword)
        binding.etHttpAddr.setText(config.httpAddr)
        binding.etHttpPort.setText(config.httpPort.toString())
        binding.etDnsServer.setText(config.dnsServer)
        binding.etCustomResponse.setText(config.customHttpResponse)
        binding.etPayload.setText(config.payload)
        binding.switchProxyAuth.isChecked = config.proxyAuthorization
        binding.switchReplaceResponse.isChecked = config.replaceHttpResponse
        binding.switchCustomPayload.isChecked = config.customPayload
        binding.switchDetectIp.isChecked = config.detectIpv4
        setTunnelTypeRadio(config.type)
    }

    private fun prepareAndStartVpn() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            startVpnAndEngine()
        }
    }

    private fun startVpnAndEngine() {
        val intent = Intent(this, HttpKuVpnService::class.java).apply {
            action = HttpKuVpnService.ACTION_START
        }
        androidx.core.content.ContextCompat.startForegroundService(this, intent)
        tunnelEngine.startTunnel()
        showLogsDialog()
    }

    private fun stopVpnAndEngine() {
        tunnelEngine.stopTunnel()
        val intent = Intent(this, HttpKuVpnService::class.java).apply {
            action = HttpKuVpnService.ACTION_STOP
        }
        startService(intent)
    }

    private fun setTunnelTypeRadio(type: TunnelType) {
        when (type) {
            TunnelType.DIRECT -> binding.rbDirect.isChecked = true
            TunnelType.HTTP -> binding.rbHttp.isChecked = true
            TunnelType.SSL -> binding.rbSsl.isChecked = true
            TunnelType.SOCKS -> binding.rbSocks.isChecked = true
            TunnelType.DNS -> binding.rbDns.isChecked = true
            TunnelType.HAPROXY -> binding.rbHaproxy.isChecked = true
        }
        updateFieldVisibilities(type)
    }

    private fun updateFieldVisibilities(type: TunnelType) {
        val showHttp = type == TunnelType.HTTP
        val showSsl = type == TunnelType.SSL
        val showDns = type == TunnelType.DNS
        val showSocks = type == TunnelType.SOCKS
        val showHaproxy = type == TunnelType.HAPROXY

        binding.tilHttpAddr.visibility = if (showHttp || showSsl || showSocks || showHaproxy) View.VISIBLE else View.GONE
        binding.tilHttpPort.visibility = if (showHttp || showSsl || showSocks || showHaproxy) View.VISIBLE else View.GONE
        binding.tilDnsServer.visibility = if (showDns) View.VISIBLE else View.GONE

        binding.containerProxyAuth.visibility = if (showHttp || showSocks) View.VISIBLE else View.GONE
        binding.containerReplaceResponse.visibility = if (showHttp || showSsl) View.VISIBLE else View.GONE
        binding.tilCustomResponse.visibility = if ((showHttp || showSsl) && binding.switchReplaceResponse.isChecked) View.VISIBLE else View.GONE
        binding.containerCustomPayload.visibility = if (showHttp || showSsl) View.VISIBLE else View.GONE
        binding.tilPayload.visibility = if ((showHttp || showSsl) && binding.switchCustomPayload.isChecked) View.VISIBLE else View.GONE
        binding.containerGenerators.visibility = if (showHttp || showSsl) View.VISIBLE else View.GONE
    }

    private fun updateEngineConfigFromFields() {
        val newConfig = tunnelEngine.config.copy(
            remoteAddr = binding.etRemoteAddr.text.toString(),
            remotePort = binding.etRemotePort.text.toString().toIntOrNull() ?: 443,
            remoteUsername = binding.etRemoteUsername.text.toString(),
            remotePassword = binding.etRemotePassword.text.toString(),
            httpAddr = binding.etHttpAddr.text.toString(),
            httpPort = binding.etHttpPort.text.toString().toIntOrNull() ?: 8080,
            dnsServer = binding.etDnsServer.text.toString(),
            customHttpResponse = binding.etCustomResponse.text.toString(),
            payload = binding.etPayload.text.toString()
        )
        tunnelEngine.updateConfig(newConfig)
        saveConfig()
    }

    private fun updateUIFromEngine() {
        val isRunning = tunnelEngine.status != TunnelStatus.DISCONNECTED
        val isEditable = !isRunning

        binding.switchDarkMode.isEnabled = isEditable

        binding.etRemoteAddr.isEnabled = isEditable
        binding.etRemotePort.isEnabled = isEditable
        binding.etRemoteUsername.isEnabled = isEditable
        binding.etRemotePassword.isEnabled = isEditable
        binding.etHttpAddr.isEnabled = isEditable
        binding.etHttpPort.isEnabled = isEditable
        binding.etDnsServer.isEnabled = isEditable
        binding.etCustomResponse.isEnabled = isEditable
        binding.etPayload.isEnabled = isEditable

        binding.switchProxyAuth.isEnabled = isEditable
        binding.switchReplaceResponse.isEnabled = isEditable
        binding.switchCustomPayload.isEnabled = isEditable
        binding.switchDetectIp.isEnabled = isEditable

        binding.btnGenerator1.isEnabled = isEditable
        binding.btnGenerator2.isEnabled = isEditable

        binding.rgTunnelType.isEnabled = isEditable
        for (i in 0 until binding.rgTunnelType.childCount) {
            binding.rgTunnelType.getChildAt(i).isEnabled = isEditable
        }

        binding.tvDetectIp.text = "detect_ipv4 ${tunnelEngine.detectedIp}"

        binding.btnStartStop.text = when (tunnelEngine.status) {
            TunnelStatus.CONNECTED -> "stop"
            TunnelStatus.CONNECTING -> "connecting..."
            TunnelStatus.DISCONNECTING -> "disconnecting..."
            TunnelStatus.WAITING_FOR_NETWORK -> "waiting for network..."
            TunnelStatus.DISCONNECTED -> "start"
        }

        when (tunnelEngine.status) {
            TunnelStatus.CONNECTED -> {
                binding.tvStatusBadge.text = "CONNECTED"
                binding.tvStatusBadge.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                binding.tvLogoIcon.setBackgroundResource(R.drawable.circle_bg_connected)
            }
            TunnelStatus.CONNECTING, TunnelStatus.DISCONNECTING, TunnelStatus.WAITING_FOR_NETWORK -> {
                binding.tvStatusBadge.text = when (tunnelEngine.status) {
                    TunnelStatus.CONNECTING -> "CONNECTING..."
                    TunnelStatus.DISCONNECTING -> "DISCONNECTING..."
                    else -> "WAITING FOR NETWORK..."
                }
                binding.tvStatusBadge.setTextColor(android.graphics.Color.parseColor("#FF9800"))
                binding.tvLogoIcon.setBackgroundResource(R.drawable.circle_bg_connecting)
            }
            TunnelStatus.DISCONNECTED -> {
                binding.tvStatusBadge.text = "DISCONNECTED"
                binding.tvStatusBadge.setTextColor(android.graphics.Color.parseColor("#F44336"))
                binding.tvLogoIcon.setBackgroundResource(R.drawable.circle_bg)
            }
        }
    }

    private fun showPayloadGeneratorDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payload_generator, null)
        val etUrl = dialogView.findViewById<TextInputEditText>(R.id.etPayloadUrl)
        val spinnerMethod = dialogView.findViewById<Spinner>(R.id.spinnerMethod)
        val spinnerInjection = dialogView.findViewById<Spinner>(R.id.spinnerInjection)
        val cbUpgradeWs = dialogView.findViewById<CheckBox>(R.id.cbUpgradeWs)
        val cbKeepAlive = dialogView.findViewById<CheckBox>(R.id.cbKeepAlive)
        val cbUserAgent = dialogView.findViewById<CheckBox>(R.id.cbUserAgent)
        val cbReferer = dialogView.findViewById<CheckBox>(R.id.cbReferer)
        val cbForwardedHost = dialogView.findViewById<CheckBox>(R.id.cbForwardedHost)
        val cbFrontQuery = dialogView.findViewById<CheckBox>(R.id.cbFrontQuery)
        val cbBackQuery = dialogView.findViewById<CheckBox>(R.id.cbBackQuery)
        val cbOnlineHost = dialogView.findViewById<CheckBox>(R.id.cbOnlineHost)
        val cbReverseProxy = dialogView.findViewById<CheckBox>(R.id.cbReverseProxy)
        val cbDualConnect = dialogView.findViewById<CheckBox>(R.id.cbDualConnect)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelPayload)
        val btnApply = dialogView.findViewById<Button>(R.id.btnApplyPayload)

        val methods = arrayOf("GET", "POST", "CONNECT", "HEAD", "PUT", "DELETE")
        val injections = arrayOf("Normal", "Front Inject", "Back Inject")

        spinnerMethod.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, methods)
        spinnerInjection.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, injections)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnApply.setOnClickListener {
            val options = PayloadGeneratorOptions(
                url = etUrl.text.toString(),
                method = methods[spinnerMethod.selectedItemPosition],
                injectionMethod = injections[spinnerInjection.selectedItemPosition],
                upgradeWebsocket = cbUpgradeWs.isChecked,
                keepAlive = cbKeepAlive.isChecked,
                userAgent = cbUserAgent.isChecked,
                referer = cbReferer.isChecked,
                forwardedHost = cbForwardedHost.isChecked,
                frontQuery = cbFrontQuery.isChecked,
                backQuery = cbBackQuery.isChecked,
                onlineHost = cbOnlineHost.isChecked,
                reverseProxy = cbReverseProxy.isChecked,
                dualConnect = cbDualConnect.isChecked
            )
            val generated = PayloadGenerator.generatePayload(options)
            binding.etPayload.setText(generated)
            updateEngineConfigFromFields()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showLogsDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_logs, null)
        val tvLogText = dialogView.findViewById<TextView>(R.id.tvLogText)
        val btnClear = dialogView.findViewById<Button>(R.id.btnClearLogs)
        val btnClose = dialogView.findViewById<Button>(R.id.btnCloseLogs)

        fun updateLogView() {
            val sb = StringBuilder()
            synchronized(tunnelEngine.logs) {
                for (log in tunnelEngine.logs) {
                    val formattedMsg = android.text.TextUtils.htmlEncode(log.message).replace("\n", "<br/>")
                    val timeStr = "[${log.formattedTime}]"
                    val isConnectedMsg = log.isSuccess || log.isHighlight || log.message.contains("VPN connected", ignoreCase = true) || log.message.contains("Authenticated", ignoreCase = true)

                    val colorHex = when {
                        log.isError -> "#F44336"
                        isConnectedMsg -> "#00BFFF" // Light Blue for connected / success
                        else -> "#FFFFFF"
                    }
                    sb.append("<font color='$colorHex'>$timeStr $formattedMsg</font><br/>")
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                tvLogText.text = android.text.Html.fromHtml(sb.toString(), android.text.Html.FROM_HTML_MODE_LEGACY)
            } else {
                @Suppress("DEPRECATION")
                tvLogText.text = android.text.Html.fromHtml(sb.toString())
            }
        }

        updateLogView()

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val logListener = {
            runOnUiThread {
                updateLogView()
            }
        }
        tunnelEngine.addListener(logListener)

        dialog.setOnDismissListener {
            tunnelEngine.removeListener(logListener)
        }

        btnClear.setOnClickListener {
            tunnelEngine.clearLogs()
            updateLogView()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
