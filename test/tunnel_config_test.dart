import 'package:flutter_test/flutter_test.dart';
import 'package:httpku/models/tunnel_config.dart';

void main() {
  group('TunnelConfig Tests', () {
    test('Default values match expected setup', () {
      final config = TunnelConfig();
      expect(config.name, 'default');
      expect(config.remoteAddr, 'id1.jagoanip.my.id');
      expect(config.remotePort, 443);
      expect(config.type, TunnelType.http);
      expect(config.httpAddr, 'bisnis.udemy.com');
      expect(config.httpPort, 8080);
      expect(config.replaceHttpResponse, isTrue);
      expect(config.customHttpResponse, 'HTTP/1.1 200 Connection established');
      expect(config.customPayload, isTrue);
      expect(config.detectIpv4, isTrue);
    });

    test('Serialization and deserialization works correctly', () {
      final config = TunnelConfig(
        remoteAddr: 'test.example.com',
        remotePort: 22,
        type: TunnelType.ssl,
      );

      final jsonStr = config.toJson();
      final restored = TunnelConfig.fromJson(jsonStr);

      expect(restored.remoteAddr, 'test.example.com');
      expect(restored.remotePort, 22);
      expect(restored.type, TunnelType.ssl);
    });

    test('CopyWith updates fields properly', () {
      final config = TunnelConfig();
      final updated = config.copyWith(
        remoteAddr: 'new.domain.com',
        type: TunnelType.direct,
      );

      expect(updated.remoteAddr, 'new.domain.com');
      expect(updated.type, TunnelType.direct);
      expect(updated.remotePort, config.remotePort);
    });
  });
}
