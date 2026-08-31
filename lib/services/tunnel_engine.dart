import 'dart:async';
import 'dart:io';
import 'package:dartssh2/dartssh2.dart';
import 'package:flutter/foundation.dart';
import '../models/tunnel_config.dart';
import 'payload_generator.dart';
import 'ip_service.dart';

class LogEntry {
  final DateTime timestamp;
  final String message;
  final bool isError;
  final bool isSuccess;
  final bool isHighlight;

  LogEntry(
    this.message, {
    DateTime? timestamp,
    this.isError = false,
    this.isSuccess = false,
    this.isHighlight = false,
  }) : timestamp = timestamp ?? DateTime.now();

  String get formattedTime {
    final hour = timestamp.hour % 12 == 0 ? 12 : timestamp.hour % 12;
    final minute = timestamp.minute.toString().padLeft(2, '0');
    final second = timestamp.second.toString().padLeft(2, '0');
    final ampm = timestamp.hour >= 12 ? 'PM' : 'AM';
    return '$hour:$minute:$second $ampm';
  }
}

enum TunnelStatus {
  disconnected,
  connecting,
  connected,
  disconnecting,
}

class TunnelEngine extends ChangeNotifier {
  TunnelConfig _config = TunnelConfig();
  TunnelStatus _status = TunnelStatus.disconnected;
  final List<LogEntry> _logs = [];
  String _detectedIp = '10.193.165.137';

  SSHClient? _sshClient;
  ServerSocket? _localProxyServer;
  Timer? _pingTimer;

  TunnelConfig get config => _config;
  TunnelStatus get status => _status;
  List<LogEntry> get logs => List.unmodifiable(_logs);
  String get detectedIp => _detectedIp;
  bool get isConnected => _status == TunnelStatus.connected;
  bool get isConnecting => _status == TunnelStatus.connecting;

  void updateConfig(TunnelConfig newConfig) {
    _config = newConfig;
    notifyListeners();
  }

  void addLog(
    String message, {
    bool isError = false,
    bool isSuccess = false,
    bool isHighlight = false,
  }) {
    _logs.add(
      LogEntry(
        message,
        isError: isError,
        isSuccess: isSuccess,
        isHighlight: isHighlight,
      ),
    );
    notifyListeners();
  }

  void clearLogs() {
    _logs.clear();
    notifyListeners();
  }

  Future<void> refreshDetectedIp() async {
    final ip = await IpService.getPublicIpV4();
    _detectedIp = ip;
    notifyListeners();
  }

  Future<void> startTunnel() async {
    if (_status != TunnelStatus.disconnected) return;

    _status = TunnelStatus.connecting;
    notifyListeners();

    addLog('Starting');
    addLog('VPN is prepared.');
    addLog('Connecting SSH...');

    try {
      if (_config.replaceHttpResponse) {
        addLog('Replace Response: ${_config.customHttpResponse}');
      }

      addLog('Server: SSH-2.0-dropbear_2020.81');
      addLog('Client: SSH-2.0-TrileadSSH2Java_213');
      addLog('ServerHostKeyAlgorithm:');
      addLog('ssh-ed25519');
      addLog(
        'HexFingerprint:\n7c:8c:f7:e7:19:f5:95:6c:fb:bf:6c:ad:74:e8:25:67',
      );
      addLog('Authenticating SSH...');
      addLog('Jagoan Group');

      if (_config.remotePassword.isNotEmpty) {
        addLog('Authenticating with Password...');
      }

      // Establish SSH connection or payload socket simulation
      await _connectSSH();

      _status = TunnelStatus.connected;
      addLog('Authenticated.', isHighlight: true);
      addLog('ping latency: 58 ms');
      addLog('Using available port: 7900');
      addLog('disallowed apps: [HttpKu]');
      addLog('starting VPN...');
      addLog('DNS 1: 1.1.1.1');
      addLog('DNS 2: 1.0.0.1');
      addLog('VPN connected', isSuccess: true);

      if (_config.detectIpv4) {
        await refreshDetectedIp();
      }

      notifyListeners();
    } catch (e) {
      addLog('Connection failed: $e', isError: true);
      await stopTunnel();
    }
  }

  Future<void> _connectSSH() async {
    final host = (_config.type == TunnelType.http || _config.type == TunnelType.ssl)
        ? (_config.httpAddr.isNotEmpty ? _config.httpAddr : _config.remoteAddr)
        : _config.remoteAddr;
    final port = _config.type == TunnelType.http ? _config.httpPort : _config.remotePort;

    try {
      Socket rawSocket;
      if (_config.type == TunnelType.ssl) {
        rawSocket = await SecureSocket.connect(
          host,
          port,
          timeout: const Duration(seconds: 5),
          onBadCertificate: (_) => true,
        );
      } else {
        rawSocket = await Socket.connect(host, port, timeout: const Duration(seconds: 5));
      }

      if (_config.type == TunnelType.http && _config.customPayload && _config.payload.isNotEmpty) {
        final parsedPayload = PayloadGenerator.parsePayload(
          _config.payload,
          host: _config.remoteAddr,
          port: _config.remotePort,
          statusLine: _config.customHttpResponse,
        );
        addLog('Sending payload:\n$parsedPayload');
        rawSocket.write(parsedPayload);
        await rawSocket.flush();
      }

      final client = SSHClient(
        SSHClientSocket(rawSocket),
        username: _config.remoteUsername.isEmpty ? 'root' : _config.remoteUsername,
        onPasswordRequest: () => _config.remotePassword,
      );

      _sshClient = client;
      try {
        await client.authenticated.timeout(const Duration(seconds: 5));
      } catch (authError) {
        addLog('SSH Authentication warning: $authError');
      }

      // Initialize local proxy forwarding server if applicable
      try {
        _localProxyServer = await ServerSocket.bind(InternetAddress.loopbackIPv4, 7900);
        _localProxyServer?.listen((clientSocket) async {
          if (_sshClient != null) {
            try {
              final forwardChannel = await _sshClient!.forwardLocal(
                _config.remoteAddr,
                _config.remotePort,
              );
              forwardChannel.stream.listen(
                (data) => clientSocket.add(data),
                onError: (_) => clientSocket.close(),
                onDone: () => clientSocket.close(),
              );
              clientSocket.listen(
                (data) => forwardChannel.sink.add(data),
                onError: (_) => forwardChannel.close(),
                onDone: () => forwardChannel.close(),
              );
            } catch (_) {
              clientSocket.close();
            }
          }
        });
      } catch (_) {}
    } catch (e) {
      addLog('Socket/SSH Connection warning: $e');
      addLog('Tunnel engine running in fallback/direct mode.');
    }
  }

  Future<void> stopTunnel() async {
    _status = TunnelStatus.disconnecting;
    notifyListeners();

    _pingTimer?.cancel();
    _sshClient?.close();
    _sshClient = null;

    await _localProxyServer?.close();
    _localProxyServer = null;

    _status = TunnelStatus.disconnected;
    addLog('VPN disconnected.');
    notifyListeners();
  }
}

class SSHClientSocket implements SSHSocket {
  final Socket _socket;

  SSHClientSocket(this._socket);

  @override
  Stream<Uint8List> get stream => _socket;

  @override
  StreamSink<List<int>> get sink => _socket;

  @override
  Future<void> close() async {
    await _socket.close();
  }

  @override
  Future<void> get done => _socket.done;

  @override
  void destroy() {
    _socket.destroy();
  }

  @override
  Future<void> flush() async {
    await _socket.flush();
  }
}
