import 'package:flutter_test/flutter_test.dart';
import 'package:sslh_sshl/services/payload_generator.dart';

void main() {
  group('PayloadGenerator Tests', () {
    test('parsePayload replaces tags correctly', () {
      const template =
          'GET / HTTP/1.1[crlf]Host: [host][crlf]Status: [status][crlf][crlf]';
      final parsed = PayloadGenerator.parsePayload(
        template,
        host: 'id1.jagoanip.my.id',
        port: 443,
        statusLine: 'HTTP/1.1 200 Connection established',
      );

      expect(
        parsed,
        'GET / HTTP/1.1\r\nHost: id1.jagoanip.my.id\r\nStatus: HTTP/1.1 200 Connection established\r\n\r\n',
      );
    });

    test('generatePayload with Normal injection', () {
      final options = PayloadGeneratorOptions(
        url: 'id1.jagoanip.my.id',
        method: 'GET',
        injectionMethod: 'Normal',
        upgradeWebsocket: true,
      );

      final payload = PayloadGenerator.generatePayload(options);
      expect(
        payload,
        'GET / HTTP/1.1[crlf]Host: id1.jagoanip.my.id[crlf]Upgrade: websocket[crlf][crlf]',
      );
    });

    test('generatePayload with Front Inject', () {
      final options = PayloadGeneratorOptions(
        url: 'id1.jagoanip.my.id',
        method: 'GET',
        injectionMethod: 'Front Inject',
        upgradeWebsocket: true,
      );

      final payload = PayloadGenerator.generatePayload(options);
      expect(
        payload,
        'GET http://id1.jagoanip.my.id/ HTTP/1.1[crlf]Host: id1.jagoanip.my.id[crlf]Upgrade: websocket[crlf][crlf]',
      );
    });
  });
}
