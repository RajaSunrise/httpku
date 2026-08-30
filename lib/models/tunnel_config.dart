import 'dart:convert';

enum TunnelType {
  direct,
  http,
  ssl,
  socks,
  dns,
  haproxy,
}

extension TunnelTypeExtension on TunnelType {
  String get displayName {
    switch (this) {
      case TunnelType.direct:
        return 'direct';
      case TunnelType.http:
        return 'http';
      case TunnelType.ssl:
        return 'ssl';
      case TunnelType.socks:
        return 'socks';
      case TunnelType.dns:
        return 'dns';
      case TunnelType.haproxy:
        return 'haproxy';
    }
  }

  static TunnelType fromString(String val) {
    return TunnelType.values.firstWhere(
      (e) => e.displayName.toLowerCase() == val.toLowerCase(),
      orElse: () => TunnelType.http,
    );
  }
}

class TunnelConfig {
  String id;
  String name;
  DateTime created;
  DateTime lastModified;
  String expiration;
  String remoteAddr;
  int remotePort;
  String remoteUsername;
  String remotePassword;
  TunnelType type;
  String httpAddr;
  int httpPort;
  bool proxyAuthorization;
  bool replaceHttpResponse;
  String customHttpResponse;
  bool customPayload;
  String payload;
  bool detectIpv4;

  TunnelConfig({
    String? id,
    this.name = 'default',
    DateTime? created,
    DateTime? lastModified,
    this.expiration = 'indeterminate',
    this.remoteAddr = 'id1.jagoanip.my.id',
    this.remotePort = 443,
    this.remoteUsername = '',
    this.remotePassword = '',
    this.type = TunnelType.http,
    this.httpAddr = 'bisnis.udemy.com',
    this.httpPort = 8080,
    this.proxyAuthorization = false,
    this.replaceHttpResponse = true,
    this.customHttpResponse = 'HTTP/1.1 200 Connection established',
    this.customPayload = true,
    this.payload =
        'GET / HTTP/1.1[crlf]Host: id1.jagoanip.my.id[crlf]Upgrade: websocket[crlf][crlf]',
    this.detectIpv4 = true,
  })  : id = id ?? DateTime.now().millisecondsSinceEpoch.toString(),
        created = created ?? DateTime.now(),
        lastModified = lastModified ?? DateTime.now();

  Map<String, dynamic> toMap() {
    return {
      'id': id,
      'name': name,
      'created': created.toIso8601String(),
      'lastModified': lastModified.toIso8601String(),
      'expiration': expiration,
      'remoteAddr': remoteAddr,
      'remotePort': remotePort,
      'remoteUsername': remoteUsername,
      'remotePassword': remotePassword,
      'type': type.displayName,
      'httpAddr': httpAddr,
      'httpPort': httpPort,
      'proxyAuthorization': proxyAuthorization,
      'replaceHttpResponse': replaceHttpResponse,
      'customHttpResponse': customHttpResponse,
      'customPayload': customPayload,
      'payload': payload,
      'detectIpv4': detectIpv4,
    };
  }

  factory TunnelConfig.fromMap(Map<String, dynamic> map) {
    return TunnelConfig(
      id: map['id'] as String?,
      name: map['name'] as String? ?? 'default',
      created: map['created'] != null ? DateTime.parse(map['created'] as String) : null,
      lastModified: map['lastModified'] != null ? DateTime.parse(map['lastModified'] as String) : null,
      expiration: map['expiration'] as String? ?? 'indeterminate',
      remoteAddr: map['remoteAddr'] as String? ?? 'id1.jagoanip.my.id',
      remotePort: map['remotePort'] as int? ?? 443,
      remoteUsername: map['remoteUsername'] as String? ?? '',
      remotePassword: map['remotePassword'] as String? ?? '',
      type: TunnelTypeExtension.fromString(map['type'] as String? ?? 'http'),
      httpAddr: map['httpAddr'] as String? ?? 'bisnis.udemy.com',
      httpPort: map['httpPort'] as int? ?? 8080,
      proxyAuthorization: map['proxyAuthorization'] as bool? ?? false,
      replaceHttpResponse: map['replaceHttpResponse'] as bool? ?? true,
      customHttpResponse: map['customHttpResponse'] as String? ?? 'HTTP/1.1 200 Connection established',
      customPayload: map['customPayload'] as bool? ?? true,
      payload: map['payload'] as String? ?? 'GET / HTTP/1.1[crlf]Host: id1.jagoanip.my.id[crlf]Upgrade: websocket[crlf][crlf]',
      detectIpv4: map['detectIpv4'] as bool? ?? true,
    );
  }

  String toJson() => json.encode(toMap());

  factory TunnelConfig.fromJson(String source) =>
      TunnelConfig.fromMap(json.decode(source) as Map<String, dynamic>);

  TunnelConfig copyWith({
    String? id,
    String? name,
    DateTime? created,
    DateTime? lastModified,
    String? expiration,
    String? remoteAddr,
    int? remotePort,
    String? remoteUsername,
    String? remotePassword,
    TunnelType? type,
    String? httpAddr,
    int? httpPort,
    bool? proxyAuthorization,
    bool? replaceHttpResponse,
    String? customHttpResponse,
    bool? customPayload,
    String? payload,
    bool? detectIpv4,
  }) {
    return TunnelConfig(
      id: id ?? this.id,
      name: name ?? this.name,
      created: created ?? this.created,
      lastModified: lastModified ?? DateTime.now(),
      expiration: expiration ?? this.expiration,
      remoteAddr: remoteAddr ?? this.remoteAddr,
      remotePort: remotePort ?? this.remotePort,
      remoteUsername: remoteUsername ?? this.remoteUsername,
      remotePassword: remotePassword ?? this.remotePassword,
      type: type ?? this.type,
      httpAddr: httpAddr ?? this.httpAddr,
      httpPort: httpPort ?? this.httpPort,
      proxyAuthorization: proxyAuthorization ?? this.proxyAuthorization,
      replaceHttpResponse: replaceHttpResponse ?? this.replaceHttpResponse,
      customHttpResponse: customHttpResponse ?? this.customHttpResponse,
      customPayload: customPayload ?? this.customPayload,
      payload: payload ?? this.payload,
      detectIpv4: detectIpv4 ?? this.detectIpv4,
    );
  }
}
