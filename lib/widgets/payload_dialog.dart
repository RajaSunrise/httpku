import 'package:flutter/material.dart';
import '../services/payload_generator.dart';

class PayloadDialog extends StatefulWidget {
  const PayloadDialog({super.key});

  @override
  State<PayloadDialog> createState() => _PayloadDialogState();
}

class _PayloadDialogState extends State<PayloadDialog> {
  final TextEditingController _urlController =
      TextEditingController(text: 'id1.jagoanip.my.id');
  String _selectedMethod = 'GET';
  String _selectedInjection = 'Normal';
  bool _keepAlive = false;
  bool _userAgent = false;
  bool _upgradeWebsocket = true;
  bool _customHeader = false;
  bool _referer = false;
  bool _forwardedHost = false;

  final List<String> _methods = ['GET', 'POST', 'CONNECT', 'HEAD', 'PUT', 'DELETE', 'OPTIONS'];
  final List<String> _injections = ['Normal', 'Front Inject', 'Back Inject'];

  void _generate() {
    final options = PayloadGeneratorOptions(
      url: _urlController.text,
      method: _selectedMethod,
      injectionMethod: _selectedInjection,
      keepAlive: _keepAlive,
      userAgent: _userAgent,
      upgradeWebsocket: _upgradeWebsocket,
      customHeader: _customHeader,
      referer: _referer,
      forwardedHost: _forwardedHost,
    );
    final payload = PayloadGenerator.generatePayload(options);
    Navigator.of(context).pop(payload);
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Payload Generator'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAlignment.start,
          children: [
            TextField(
              controller: _urlController,
              decoration: const InputDecoration(
                labelText: 'URL / Host',
              ),
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              value: _selectedMethod,
              decoration: const InputDecoration(labelText: 'Request Method'),
              items: _methods
                  .map((m) => DropdownMenuItem(value: m, child: Text(m)))
                  .toList(),
              onChanged: (val) {
                if (val != null) setState(() => _selectedMethod = val);
              },
            ),
            const SizedBox(height: 12),
            DropdownButtonFormField<String>(
              value: _selectedInjection,
              decoration: const InputDecoration(labelText: 'Injection Method'),
              items: _injections
                  .map((i) => DropdownMenuItem(value: i, child: Text(i)))
                  .toList(),
              onChanged: (val) {
                if (val != null) setState(() => _selectedInjection = val);
              },
            ),
            const SizedBox(height: 12),
            CheckboxListTile(
              title: const Text('Upgrade WebSocket'),
              value: _upgradeWebsocket,
              onChanged: (val) => setState(() => _upgradeWebsocket = val ?? true),
              contentPadding: EdgeInsets.zero,
            ),
            CheckboxListTile(
              title: const Text('Keep-Alive'),
              value: _keepAlive,
              onChanged: (val) => setState(() => _keepAlive = val ?? false),
              contentPadding: EdgeInsets.zero,
            ),
            CheckboxListTile(
              title: const Text('User-Agent'),
              value: _userAgent,
              onChanged: (val) => setState(() => _userAgent = val ?? false),
              contentPadding: EdgeInsets.zero,
            ),
            CheckboxListTile(
              title: const Text('Referer'),
              value: _referer,
              onChanged: (val) => setState(() => _referer = val ?? false),
              contentPadding: EdgeInsets.zero,
            ),
            CheckboxListTile(
              title: const Text('X-Forwarded-Host'),
              value: _forwardedHost,
              onChanged: (val) => setState(() => _forwardedHost = val ?? false),
              contentPadding: EdgeInsets.zero,
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: const Text('Batal'),
        ),
        ElevatedButton(
          onPressed: _generate,
          child: const Text('Generate Payload'),
        ),
      ],
    );
  }
}
