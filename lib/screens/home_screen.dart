import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../main.dart';
import '../models/tunnel_config.dart';
import '../services/tunnel_engine.dart';
import '../widgets/log_dialog.dart';
import '../widgets/payload_dialog.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late TextEditingController _remoteAddrController;
  late TextEditingController _remotePortController;
  late TextEditingController _remoteUsernameController;
  late TextEditingController _remotePasswordController;
  late TextEditingController _httpAddrController;
  late TextEditingController _httpPortController;
  late TextEditingController _customHttpResponseController;
  late TextEditingController _payloadController;

  @override
  void initState() {
    super.initState();
    final engine = Provider.of<TunnelEngine>(context, listen: false);
    final config = engine.config;

    _remoteAddrController = TextEditingController(text: config.remoteAddr);
    _remotePortController =
        TextEditingController(text: config.remotePort.toString());
    _remoteUsernameController =
        TextEditingController(text: config.remoteUsername);
    _remotePasswordController =
        TextEditingController(text: config.remotePassword);
    _httpAddrController = TextEditingController(text: config.httpAddr);
    _httpPortController =
        TextEditingController(text: config.httpPort.toString());
    _customHttpResponseController =
        TextEditingController(text: config.customHttpResponse);
    _payloadController = TextEditingController(text: config.payload);
  }

  @override
  void dispose() {
    _remoteAddrController.dispose();
    _remotePortController.dispose();
    _remoteUsernameController.dispose();
    _remotePasswordController.dispose();
    _httpAddrController.dispose();
    _httpPortController.dispose();
    _customHttpResponseController.dispose();
    _payloadController.dispose();
    super.dispose();
  }

  void _updateEngineConfig(TunnelEngine engine) {
    final updated = engine.config.copyWith(
      remoteAddr: _remoteAddrController.text,
      remotePort: int.tryParse(_remotePortController.text) ?? 443,
      remoteUsername: _remoteUsernameController.text,
      remotePassword: _remotePasswordController.text,
      httpAddr: _httpAddrController.text,
      httpPort: int.tryParse(_httpPortController.text) ?? 8080,
      customHttpResponse: _customHttpResponseController.text,
      payload: _payloadController.text,
    );
    engine.updateConfig(updated);
  }

  void _showPayloadGeneratorDialog() async {
    final result = await showDialog<String>(
      context: context,
      builder: (context) => const PayloadDialog(),
    );
    if (result != null && result.isNotEmpty) {
      setState(() {
        _payloadController.text = result;
      });
      final engine = Provider.of<TunnelEngine>(context, listen: false);
      _updateEngineConfig(engine);
    }
  }

  void _showLogsDialog() {
    showDialog(
      context: context,
      builder: (context) => const LogDialog(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final themeProvider = Provider.of<ThemeProvider>(context);
    final engine = Provider.of<TunnelEngine>(context);
    final config = engine.config;

    return Scaffold(
      appBar: AppBar(
        titleSpacing: 16,
        toolbarHeight: 70,
        title: Row(
          children: [
            Container(
              width: 44,
              height: 44,
              decoration: const BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.white24,
              ),
              child: const Center(
                child: Text(
                  'SSLH',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
            const SizedBox(width: 12),
            Column(
              crossAxisAlignment: CrossAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: const [
                Text(
                  'SSLH/SSHL',
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                SizedBox(height: 2),
                Text(
                  '1.0 build 33 ndk 23.1.777...',
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey,
                  ),
                ),
              ],
            ),
          ],
        ),
        actions: [
          const Text('dark', style: TextStyle(fontSize: 14)),
          Switch(
            value: themeProvider.isDark,
            onChanged: (val) {
              themeProvider.toggleTheme(val);
            },
            activeColor: Colors.blue,
          ),
          PopupMenuButton<String>(
            onSelected: (val) {
              if (val == 'logs') {
                _showLogsDialog();
              }
            },
            itemBuilder: (context) => [
              const PopupMenuItem(
                value: 'logs',
                child: Text('View Logs'),
              ),
            ],
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        child: Column(
          crossAxisAlignment: CrossAlignment.start,
          children: [
            const Text(
              'config',
              style: TextStyle(color: Colors.grey, fontSize: 13),
            ),
            const SizedBox(height: 4),
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAlignment.start,
                    children: [
                      Text(
                        config.name,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                      const SizedBox(height: 2),
                      const Text(
                        'Last Modified: Sun Aug 30 16:15:44 GMT+08:00 2026',
                        style: TextStyle(fontSize: 12, color: Colors.grey),
                      ),
                      const Text(
                        'Created: Sun Aug 30 16:15:44 GMT+08:00 2026',
                        style: TextStyle(fontSize: 12, color: Colors.grey),
                      ),
                      Text(
                        'Expiration: ${config.expiration}',
                        style: const TextStyle(
                            fontSize: 12, color: Colors.grey),
                      ),
                    ],
                  ),
                ),
                const Icon(Icons.arrow_drop_down, color: Colors.grey),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: Text(
                    config.name,
                    style: const TextStyle(fontSize: 16, color: Colors.grey),
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.edit_outlined, color: Colors.grey),
                  onPressed: () {},
                ),
              ],
            ),
            const Divider(height: 1),
            const SizedBox(height: 12),
            TextField(
              controller: _remoteAddrController,
              decoration: const InputDecoration(
                labelText: 'remote_addr',
              ),
              onChanged: (_) => _updateEngineConfig(engine),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _remotePortController,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'remote_port',
              ),
              onChanged: (_) => _updateEngineConfig(engine),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _remoteUsernameController,
              obscureText: true,
              decoration: const InputDecoration(
                labelText: 'remote_username',
              ),
              onChanged: (_) => _updateEngineConfig(engine),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _remotePasswordController,
              obscureText: true,
              decoration: const InputDecoration(
                labelText: 'remote_password',
              ),
              onChanged: (_) => _updateEngineConfig(engine),
            ),
            const SizedBox(height: 16),
            const Text(
              'type',
              style: TextStyle(color: Colors.grey, fontSize: 13),
            ),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: TunnelType.values.map((type) {
                  return Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Radio<TunnelType>(
                        value: type,
                        groupValue: config.type,
                        activeColor: Colors.blue,
                        onChanged: (val) {
                          if (val != null) {
                            engine.updateConfig(config.copyWith(type: val));
                          }
                        },
                      ),
                      Text(
                        type.displayName,
                        style: const TextStyle(fontSize: 14),
                      ),
                      const SizedBox(width: 8),
                    ],
                  );
                }).toList(),
              ),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _httpAddrController,
              decoration: const InputDecoration(
                labelText: 'http_addr',
              ),
              onChanged: (_) => _updateEngineConfig(engine),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: _httpPortController,
              keyboardType: TextInputType.number,
              decoration: const InputDecoration(
                labelText: 'http_port',
              ),
              onChanged: (_) => _updateEngineConfig(engine),
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'proxy_authorization',
                  style: TextStyle(fontSize: 15),
                ),
                Switch(
                  value: config.proxyAuthorization,
                  onChanged: (val) {
                    engine.updateConfig(
                        config.copyWith(proxyAuthorization: val));
                  },
                  activeColor: Colors.blue,
                ),
              ],
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'replace_http_response',
                  style: TextStyle(fontSize: 15),
                ),
                Switch(
                  value: config.replaceHttpResponse,
                  onChanged: (val) {
                    engine.updateConfig(
                        config.copyWith(replaceHttpResponse: val));
                  },
                  activeColor: Colors.blue,
                ),
              ],
            ),
            const SizedBox(height: 4),
            TextField(
              controller: _customHttpResponseController,
              decoration: const InputDecoration(
                labelText: 'custom_http_response',
              ),
              onChanged: (_) => _updateEngineConfig(engine),
            ),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'custom_payload',
                  style: TextStyle(fontSize: 15),
                ),
                Switch(
                  value: config.customPayload,
                  onChanged: (val) {
                    engine.updateConfig(
                        config.copyWith(customPayload: val));
                  },
                  activeColor: Colors.blue,
                ),
              ],
            ),
            const SizedBox(height: 4),
            TextField(
              controller: _payloadController,
              maxLines: 4,
              decoration: const InputDecoration(
                labelText: 'payload',
              ),
              onChanged: (_) => _updateEngineConfig(engine),
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.grey.shade300,
                      foregroundColor: Colors.black87,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(4),
                      ),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                    onPressed: _showPayloadGeneratorDialog,
                    child: const Text('generator'),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.grey.shade300,
                      foregroundColor: Colors.black87,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(4),
                      ),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                    onPressed: _showPayloadGeneratorDialog,
                    child: const Text('generator'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'detect_ipv4 ${engine.detectedIp}',
                  style: const TextStyle(fontSize: 15),
                ),
                Switch(
                  value: config.detectIpv4,
                  onChanged: (val) {
                    engine.updateConfig(config.copyWith(detectIpv4: val));
                    if (val) {
                      engine.refreshDetectedIp();
                    }
                  },
                  activeColor: Colors.blue,
                ),
              ],
            ),
            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.grey.shade300,
                  foregroundColor: Colors.black87,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(4),
                  ),
                ),
                onPressed: () {
                  if (engine.isConnected || engine.isConnecting) {
                    engine.stopTunnel();
                  } else {
                    engine.startTunnel();
                    _showLogsDialog();
                  }
                },
                child: Text(
                  engine.isConnected
                      ? 'stop'
                      : engine.isConnecting
                          ? 'connecting...'
                          : 'start',
                  style: const TextStyle(fontSize: 16),
                ),
              ),
            ),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }
}
