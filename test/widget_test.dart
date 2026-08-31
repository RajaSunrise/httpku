import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:httpku/main.dart';
import 'package:httpku/services/tunnel_engine.dart';

void main() {
  testWidgets('App renders main UI controls correctly', (WidgetTester tester) async {
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => TunnelEngine()),
          ChangeNotifierProvider(create: (_) => ThemeProvider(false)),
        ],
        child: const HttpKuApp(),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('HttpKu'), findsNWidgets(2));
    expect(find.text('1.0 build 33 ndk 23.1.777...'), findsOneWidget);
    expect(find.text('remote_addr'), findsOneWidget);
    expect(find.text('remote_port'), findsOneWidget);
    expect(find.text('http_addr'), findsOneWidget);
    expect(find.text('http_port'), findsOneWidget);
    expect(find.text('custom_http_response'), findsOneWidget);
    expect(find.text('payload'), findsOneWidget);
    expect(find.text('start'), findsOneWidget);
  });
}
