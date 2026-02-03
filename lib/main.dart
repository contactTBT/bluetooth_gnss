import 'dart:async';

import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:permission_handler/permission_handler.dart';

import 'home.dart';

const String bleUartModeKey = 'ble_uart_mode';
const String bleQstarzModeKey = 'ble_qstarz_mode';
const String batteryOptAskedKey = 'battery_optimization_asked';
late final PrefServiceShared prefService;

Future<void> requestBatteryOptimization(BuildContext context) async {
  // Check if we've already asked
  final hasAsked = (prefService.get(batteryOptAskedKey) as bool?) ?? false;
  if (hasAsked) return;

  // Check current status
  final status = await Permission.ignoreBatteryOptimizations.status;
  if (status.isGranted) {
    await prefService.set(batteryOptAskedKey, true);
    return;
  }

  // Show explanation dialog
  if (!context.mounted) return;
  final shouldRequest = await showDialog<bool>(
    context: context,
    builder: (BuildContext context) => AlertDialog(
      title: const Text('Battery Optimization'),
      content: const Text(
        'To ensure reliable background GPS/GNSS connectivity, this app needs to be exempted from battery optimization.\n\n'
        'This allows the app to maintain your Bluetooth connection and provide accurate location data even when running in the background.',
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: const Text('Not Now'),
        ),
        TextButton(
          onPressed: () => Navigator.of(context).pop(true),
          child: const Text('Allow'),
        ),
      ],
    ),
  );

  // Mark as asked regardless of user choice
  await prefService.set(batteryOptAskedKey, true);

  // Request permission if user agreed
  if (shouldRequest == true) {
    await Permission.ignoreBatteryOptimizations.request();
  }
}

Future<void> main() async {
  WidgetsFlutterBinding
      .ensureInitialized(); //https://stackoverflow.com/questions/57689492/flutter-unhandled-exception-servicesbinding-defaultbinarymessenger-was-accesse
  prefService = await PrefServiceShared.init(prefix: "pref_");
  await prefService.setDefaultValues({
    'reconnect': false,
    'secure': true,
    'device_cep': "5.0",
    'check_settings_location': false,
    'log_bt_rx': false,
    'disable_ntrip': false,
    'ble_gap_scan_mode': false,
    bleUartModeKey: false,
    bleQstarzModeKey: false,
    'autostart': false,
    'list_nearest_streams_first': true,
    'ntrip_host': "crtk.net",
    'ntrip_port': "2101",
    'ntrip_user': "c",
    'ntrip_pass': "c",
    "mock_timestamp_use_system_time": true,
    "mock_timestamp_offset_secs": "0.0",
    "mock_lat_offset_meters": "0.0",
    "mock_lon_offset_meters": "0.0",
    "mock_alt_offset_meters": "0.0",
    batteryOptAskedKey: false,
  });
  runApp(App());
}

class App extends StatelessWidget {
  const App({super.key});
  @override
  Widget build(BuildContext context) {
    return PrefService(
        service: prefService,
        child: MaterialApp(
          title: 'Bluetooth GNSS',
          theme: ThemeData.light(
            useMaterial3: true,
          ),
          home: const HomeScreen(),
        ));
  }
}
