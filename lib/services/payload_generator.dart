class PayloadGeneratorOptions {
  final String url;
  final String method; // GET, POST, CONNECT, etc.
  final String injectionMethod; // Normal, Front Inject, Back Inject, Dual Real, Split
  final bool keepAlive;
  final bool userAgent;
  final bool upgradeWebsocket;
  final bool customHeader;
  final bool referer;
  final bool forwardedHost;
  final bool backQuery;
  final bool frontQuery;

  PayloadGeneratorOptions({
    this.url = 'id1.jagoanip.my.id',
    this.method = 'GET',
    this.injectionMethod = 'Normal',
    this.keepAlive = false,
    this.userAgent = false,
    this.upgradeWebsocket = true,
    this.customHeader = false,
    this.referer = false,
    this.forwardedHost = false,
    this.backQuery = false,
    this.frontQuery = false,
  });
}

class PayloadGenerator {
  /// Parses tags inside the payload string and replaces them with appropriate values.
  static String parsePayload(
    String payload, {
    required String host,
    required int port,
    String statusLine = 'HTTP/1.1 200 Connection established',
  }) {
    String parsed = payload;

    parsed = parsed.replaceAll('[crlf]', '\r\n');
    parsed = parsed.replaceAll('[cr]', '\r');
    parsed = parsed.replaceAll('[lf]', '\n');
    parsed = parsed.replaceAll('[protocol]', 'HTTP/1.1');
    parsed = parsed.replaceAll('[host]', host);
    parsed = parsed.replaceAll('[port]', port.toString());
    parsed = parsed.replaceAll(
      '[host_port]',
      port == 80 || port == 443 ? host : '$host:$port',
    );
    parsed = parsed.replaceAll(
      '[ua]',
      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    );
    parsed = parsed.replaceAll(
      '[raw]',
      'CONNECT [host_port] [protocol][crlf]Host: [host_port][crlf]',
    );
    parsed = parsed.replaceAll(
      '[real_raw]',
      'CONNECT [host_port] [protocol][crlf]',
    );
    parsed = parsed.replaceAll('[status]', statusLine);

    return parsed;
  }

  /// Generates a standard HTTP payload string from user options.
  static String generatePayload(PayloadGeneratorOptions options) {
    String host = options.url.trim();
    if (host.isEmpty) host = 'id1.jagoanip.my.id';

    StringBuffer sb = StringBuffer();

    if (options.injectionMethod == 'Front Inject') {
      sb.write('${options.method} http://$host/ HTTP/1.1[crlf]');
      sb.write('Host: $host[crlf]');
    } else if (options.injectionMethod == 'Back Inject') {
      sb.write('CONNECT [host_port] HTTP/1.1[crlf]');
      sb.write('Host: [host_port][crlf]');
      sb.write('${options.method} http://$host/ HTTP/1.1[crlf]');
    } else {
      // Normal
      sb.write('${options.method} / HTTP/1.1[crlf]');
      sb.write('Host: $host[crlf]');
    }

    if (options.upgradeWebsocket) {
      sb.write('Upgrade: websocket[crlf]');
    }

    if (options.keepAlive) {
      sb.write('Connection: Keep-Alive[crlf]');
    }

    if (options.userAgent) {
      sb.write('User-Agent: [ua][crlf]');
    }

    if (options.referer) {
      sb.write('Referer: http://$host/[crlf]');
    }

    if (options.forwardedHost) {
      sb.write('X-Forwarded-Host: $host[crlf]');
    }

    sb.write('[crlf]');
    return sb.toString();
  }
}
