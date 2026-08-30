import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../services/tunnel_engine.dart';

class LogDialog extends StatelessWidget {
  const LogDialog({super.key});

  @override
  Widget build(BuildContext context) {
    final engine = Provider.of<TunnelEngine>(context);
    final logs = engine.logs;

    return Dialog(
      backgroundColor: const Color(0xFF1E1E24),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(8),
      ),
      child: Container(
        padding: const EdgeInsets.all(16),
        constraints: const BoxConstraints(maxHeight: 480, maxWidth: 400),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'Log',
              style: TextStyle(
                color: Colors.white,
                fontSize: 20,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 12),
            Expanded(
              child: Container(
                decoration: BoxDecoration(
                  color: Colors.black45,
                  borderRadius: BorderRadius.circular(4),
                ),
                padding: const EdgeInsets.all(8),
                child: ListView.builder(
                  itemCount: logs.length,
                  itemBuilder: (context, index) {
                    final log = logs[index];
                    Color textColor = Colors.grey.shade400;
                    if (log.isSuccess) {
                      textColor = Colors.green;
                    } else if (log.isError) {
                      textColor = Colors.red;
                    } else if (log.isHighlight) {
                      textColor = Colors.blue;
                    }

                    return Padding(
                      padding: const EdgeInsets.symmetric(vertical: 2),
                      child: Text(
                        '${log.formattedTime}\n${log.message}',
                        style: TextStyle(
                          color: textColor,
                          fontSize: 13,
                          fontFamily: 'monospace',
                        ),
                      ),
                    );
                  },
                ),
              ),
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                TextButton(
                  onPressed: () {
                    engine.clearLogs();
                  },
                  child: const Text(
                    'Potong',
                    style: TextStyle(color: Colors.blue),
                  ),
                ),
                Row(
                  children: [
                    TextButton(
                      onPressed: () => Navigator.of(context).pop(),
                      child: const Text(
                        'Batal',
                        style: TextStyle(color: Colors.blue),
                      ),
                    ),
                    const SizedBox(width: 8),
                    TextButton(
                      onPressed: () {
                        final text = logs
                            .map((l) => '${l.formattedTime} ${l.message}')
                            .join('\n');
                        Clipboard.setData(ClipboardData(text: text));
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('Log copied to clipboard'),
                            duration: Duration(seconds: 2),
                          ),
                        );
                      },
                      child: const Text(
                        'Salin',
                        style: TextStyle(color: Colors.blue),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
