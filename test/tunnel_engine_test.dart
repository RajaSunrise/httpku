import 'package:flutter_test/flutter_test.dart';
import 'package:httpku/services/tunnel_engine.dart';
import 'package:httpku/models/tunnel_config.dart';

void main() {
  group('TunnelEngine Tests', () {
    test('Initial status is disconnected and logs empty', () {
      final engine = TunnelEngine();
      expect(engine.status, TunnelStatus.disconnected);
      expect(engine.isConnected, isFalse);
      expect(engine.logs, isEmpty);
    });

    test('Start and stop tunnel changes state and records logs', () async {
      final engine = TunnelEngine();
      expect(engine.status, TunnelStatus.disconnected);

      final startFuture = engine.startTunnel();
      expect(engine.isConnecting || engine.isConnected, isTrue);

      await startFuture;
      expect(engine.status, TunnelStatus.connected);
      expect(engine.isConnected, isTrue);
      expect(engine.logs, isNotEmpty);

      await engine.stopTunnel();
      expect(engine.status, TunnelStatus.disconnected);
      expect(engine.isConnected, isFalse);
      expect(engine.logs.last.message, contains('disconnected'));
    });

    test('Clear logs empties log list', () {
      final engine = TunnelEngine();
      engine.addLog('Test log entry');
      expect(engine.logs.length, 1);

      engine.clearLogs();
      expect(engine.logs, isEmpty);
    });

    test('Config updates correctly', () {
      final engine = TunnelEngine();
      final newConfig = TunnelConfig(remoteAddr: 'ssh.server.com', remotePort: 22);
      engine.updateConfig(newConfig);

      expect(engine.config.remoteAddr, 'ssh.server.com');
      expect(engine.config.remotePort, 22);
    });
  });
}
