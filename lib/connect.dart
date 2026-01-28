import 'dart:developer' as developer;

import 'package:bluetooth_gnss/gnss_device.dart';
import 'package:bluetooth_gnss/main.dart';
import 'package:bluetooth_gnss/utils.dart';
import 'package:bluetooth_gnss/utils_ui.dart';
import 'package:flutter/material.dart';
import 'package:pref/pref.dart';

import 'channels.dart' as channels;
import 'channels.dart' show methodChannel, paramMap, getBdMap, getUsbDevices, isCoarseLocationEnabled, checkPermissions, isBluetoothOn, getSelectedBdaddr, getSelectedBdname, isMockLocationEnabled;
import 'const.dart';
import 'home.dart';

enum ConnectState {
  Loading,
  PendingRequirements,
  ReadyToConnect,
  Connecting,
  Connected
}

ValueNotifier<ConnectState> connectState = ValueNotifier(ConnectState.Loading);
ValueNotifier<String> connectStatus = ValueNotifier("Loading status...");
ValueNotifier<String> connectSelectedDevice =
    ValueNotifier("Loading selected device...");
ValueNotifier<bool> isBtConnected = ValueNotifier(false);
ValueNotifier<DateTime> setLiveArgsTs = ValueNotifier(DateTime.now());
ValueNotifier<bool> isQstarz = ValueNotifier(false);
ValueNotifier<bool> isBtConnThreadConnecting = ValueNotifier(false);
ValueNotifier<int> mockLocationSetTs = ValueNotifier(0);
ValueNotifier<String> mockLocationSetStatus = ValueNotifier(waitingDev);
ValueNotifier<bool> isNtripConnected = ValueNotifier(false);
ValueNotifier<int> ntripPacketsCount = ValueNotifier(0);
final ValueNotifier<Map<String, Icon>> checkStateMapIcon = ValueNotifier({});
final ValueNotifier<Map<String, String>> bdMapNotifier = ValueNotifier({});

/// Unified device list containing both Bluetooth and USB devices
final ValueNotifier<List<GnssDevice>> unifiedDeviceListNotifier = ValueNotifier([]);

/// Currently selected device (unified model)
final ValueNotifier<GnssDevice?> selectedDeviceNotifier = ValueNotifier(null);

/// Load and merge Bluetooth and USB devices into unified list
Future<void> loadUnifiedDeviceList() async {
  List<GnssDevice> devices = [];

  // Load Bluetooth devices
  try {
    Map<String, String> bdMap = await getBdMap();
    for (var entry in bdMap.entries) {
      devices.add(GnssDevice.fromBluetooth(entry.key, entry.value));
    }
  } catch (e) {
    developer.log('loadUnifiedDeviceList BT error: $e');
  }

  // Load USB devices
  try {
    List<Map<String, dynamic>> usbDevices = await getUsbDevices();
    for (var usbDevice in usbDevices) {
      devices.add(GnssDevice.fromUsb(usbDevice));
    }
  } catch (e) {
    developer.log('loadUnifiedDeviceList USB error: $e');
  }

  unifiedDeviceListNotifier.value = devices;
  developer.log('loadUnifiedDeviceList: ${devices.length} devices (BT+USB)');
}

/// Get selected device from preferences
GnssDevice? getSelectedDevice() {
  String targetDevice = prefService.get('target_device') ?? '';
  if (targetDevice.isEmpty) {
    // Fallback to old target_bdaddr for backwards compatibility
    String bdaddr = prefService.get('target_bdaddr') ?? '';
    if (bdaddr.isNotEmpty) {
      // Find matching BT device
      for (var device in unifiedDeviceListNotifier.value) {
        if (device.type == DeviceConnectionType.bluetooth &&
            device.bluetoothAddress == bdaddr) {
          return device;
        }
      }
    }
    return null;
  }

  // Find device by unified ID
  for (var device in unifiedDeviceListNotifier.value) {
    if (device.id == targetDevice) {
      return device;
    }
  }
  return null;
}

/// Save selected device to preferences
Future<void> setSelectedDevice(GnssDevice? device) async {
  if (device == null) {
    await prefService.set('target_device', '');
    await prefService.set('target_bdaddr', '');
    selectedDeviceNotifier.value = null;
    return;
  }

  await prefService.set('target_device', device.id);

  // Also set target_bdaddr for backwards compatibility with BT devices
  if (device.type == DeviceConnectionType.bluetooth) {
    await prefService.set('target_bdaddr', device.bluetoothAddress ?? '');
  } else {
    // Clear bdaddr if USB device selected
    await prefService.set('target_bdaddr', '');
  }

  selectedDeviceNotifier.value = device;
  developer.log('setSelectedDevice: ${device.displayName}');
}

void paramMapSubscribe(String param)
{
  if (!paramMap.containsKey(param)) {
    paramMap[param] = ValueNotifier<dynamic>('');
  }
}

(String, String) getLatLon()
{
  var lat = paramMap['lat']?.value;
  var lon = paramMap['lon']?.value;
  if (lat != null && lon != null) {
    if (lat is double && lon is double) {
      String lats = lat.toStringAsFixed(POS_FRACTION_DIGITS);
      String lons = lon.toStringAsFixed(POS_FRACTION_DIGITS);
      return (lats, lons);
    }
  }
  return ("", "");
}

String getLatLonCsv()
{
  var (lat, lon) = getLatLon();
  if (lat.isEmpty || lon.isEmpty) {
    return "";
  }
  return "$lat,$lon"; //for share gmaps - no space after comma
}

Future<void> onFloatingButtonTap() async {
  ConnectState state = connectState.value;
  switch (state) {
    case ConnectState.Loading:
    case ConnectState.PendingRequirements:
      await toast("Not Ready: ${connectStatus.value}");
    case ConnectState.ReadyToConnect:
      await connect();
    case ConnectState.Connecting:
      await toast("Connecting, Please wait...");
    case ConnectState.Connected:
      await disconnect();
  }
}

Future<void> disconnect() async {
  try {
    //call it in any case just to be sure service it is stopped (.close()) method called
    await methodChannel.invokeMethod('disconnect');
  } on Exception catch (e, trace) {
    developer.log("WARNING: disconnect failed exception: $e $trace");
  }
}

Future<void> connect() async {
  developer.log("main.dart connect() start");

  // Get selected device (unified model)
  GnssDevice? selectedDevice = getSelectedDevice();

  // Check if we have a USB device selected
  if (selectedDevice != null && selectedDevice.type == DeviceConnectionType.usb) {
    await connectUsb(selectedDevice);
    return;
  }

  // Bluetooth connection flow (original logic)
  await connectBluetooth();
}

/// Connect to a USB GNSS device
Future<void> connectUsb(GnssDevice device) async {
  developer.log("connectUsb() start: ${device.displayName}");

  if (isBtConnected.value) {
    throw "Already connected...";
  }

  int? deviceId = device.usbDeviceId;
  if (deviceId == null) {
    throw "Invalid USB device ID";
  }

  paramMap.clear();
  String status = "unknown";

  // Get log URI if logging is enabled
  String logBtRxLogUri = prefService.get('log_bt_rx_log_uri') ?? "";

  // Build connection parameters including NTRIP settings (same as Bluetooth)
  Map<String, dynamic> connectionParams = {
    'device_cep': prefService.get('device_cep') ?? "5.0",
    'log_bt_rx_log_uri': logBtRxLogUri,
    'reconnect': prefService.get('reconnect') ?? false,  // Auto-reconnect on disconnect
    'disable_ntrip': prefService.get('disable_ntrip') ?? false,
    'ntrip_host': prefService.get('ntrip_host'),
    'ntrip_port': prefService.get('ntrip_port'),
    'ntrip_mountpoint': prefService.get('ntrip_mountpoint'),
    'ntrip_user': prefService.get('ntrip_user'),
    'ntrip_pass': prefService.get('ntrip_pass'),
    'mock_timestamp_use_system_time': true,
    'mock_timestamp_offset_secs': double.parse(prefService.get('mock_timestamp_offset_secs') ?? "0.0"),
    'mock_lat_offset_meters': double.parse(prefService.get('mock_lat_offset_meters') ?? "0.0"),
    'mock_lon_offset_meters': double.parse(prefService.get('mock_lon_offset_meters') ?? "0.0"),
    'mock_alt_offset_meters': double.parse(prefService.get('mock_alt_offset_meters') ?? "0.0"),
  };

  try {
    developer.log("connectUsb() invoking connectUsb channel with params: $connectionParams");
    final bool ret = await channels.connectUsb(deviceId, connectionParams);

    if (ret) {
      status = "Connecting to USB device - please wait ...";
    } else {
      status = "Failed to initiate USB connection...";
    }
  } catch (e, t) {
    status = "Failed to start USB connection: '$e'";
    developer.log("$status $t");
  }

  connectStatus.value = status;
  developer.log("connectUsb() done");
}

/// Connect to a Bluetooth GNSS device (original connect logic)
Future<void> connectBluetooth() async {
  developer.log("connectBluetooth() start");
  String log_bt_rx_log_uri = prefService.get('log_bt_rx_log_uri') ?? "";
  bool autostart = prefService.get('autostart') ?? false;
  bool gapMode = prefService.get('ble_gap_scan_mode') ?? false;
  String log_uri = prefService.get('log_bt_rx_log_uri') as String? ?? "";
  if (log_bt_rx_log_uri.isNotEmpty) {
    bool writeEnabled = false;
    writeEnabled =
        (await methodChannel.invokeMethod('is_write_enabled')) as bool? ??
            false;
    if (writeEnabled == false) {
      throw "Write external storage permission required for data loggging...";
    }
    bool canCreateFile = false;
    canCreateFile = (await methodChannel.invokeMethod(
            'test_can_create_file_in_chosen_folder',
            {"log_bt_rx_log_uri": log_uri})) as bool? ??
        false;
    if (canCreateFile == false) {
      throw "Please go to Settings > re-tick 'Enable logging' (failed to access chosen log folder)";
    }
  }
  if (gapMode) {
    bool coarseLocationEnabled = await isCoarseLocationEnabled();
    if (coarseLocationEnabled == false) {
      throw "Coarse Locaiton permission required for BLE GAP mode...";
    }
  }

  if (isBtConnected.value) {
    throw "Already connected...";
  }

  if (isBtConnThreadConnecting.value) {
    throw "Already connecting, please wait...";
  }

  bool connecting = false;
  connecting =
      (await methodChannel.invokeMethod('is_conn_thread_alive')) as bool? ??
          false;
  if (connecting) {
    throw "Already connecting, please wait...";
  }

  String bdaddr = prefService.get("target_bdaddr") as String? ?? "";
  if (bdaddr.isEmpty) {
    throw "No bluetooth device selected in settings";
  }
  developer.log("connectBluetooth() start1");
  paramMap.clear();

  String status = "unknown";
  try {
    developer.log("connectBluetooth() start connect start");
    final bool ret = (await methodChannel.invokeMethod('connect', {
          "bdaddr": bdaddr,
          'secure': prefService.get('secure') ?? true,
          "device_cep":  prefService.get('device_cep') ?? "5.0",
          'reconnect': prefService.get('reconnect') ?? false, //TODO: retest/recode this feature - not well tested - users say no way to stop/disconnect when fail,
          'ble_gap_scan_mode': gapMode,
          'log_bt_rx_log_uri': log_bt_rx_log_uri,
          'disable_ntrip': prefService.get('disable_ntrip') ?? false,
          'ntrip_host': prefService.get('ntrip_host'),
          'ntrip_port': prefService.get('ntrip_port'),
          'ntrip_mountpoint': prefService.get('ntrip_mountpoint'),
          'ntrip_user': prefService.get('ntrip_user'),
          'ntrip_pass': prefService.get('ntrip_pass'),
          'autostart': autostart,

      'mock_timestamp_use_system_time': true,
      'mock_timestamp_offset_secs': double.parse(prefService.get('mock_timestamp_offset_secs') ?? "0.0"),
      'mock_lat_offset_meters': double.parse(prefService.get('mock_lat_offset_meters') ?? "0.0"),
      'mock_lon_offset_meters': double.parse(prefService.get('mock_lon_offset_meters') ?? "0.0"),
      'mock_alt_offset_meters': double.parse(prefService.get('mock_alt_offset_meters') ?? "0.0"),

        })) as bool? ??
        false;
    developer.log("connectBluetooth() start connect done");
    if (ret) {
      status = "Connecting - please wait ...";
    } else {
      status = "Failed to connect...";
    }
    developer.log("connectBluetooth() start2");
  } catch (e, t) {
    status = "Failed to start connection: '${e}'";
    developer.log(status+"$t");
  }

  developer.log("connectBluetooth() start3");

  connectStatus.value = status;

  developer.log("connectBluetooth() start4");

  developer.log("connectBluetooth() done");
}

Future<String> getSelectedBdSummary(BasePrefService prefService) async {
  //developer.log("get_selected_bd_summary 0");
  String ret = '';
  String bdaddr = getSelectedBdaddr(prefService);
  //developer.log("get_selected_bd_summary selected bdaddr: $bdaddr");
  String bdname = await getSelectedBdname(prefService);
  if (bdname.startsWith("QSTARZ")) {
    isQstarz.value = true;
  } else {
    isQstarz.value = false;
  }
  if (bdaddr.isEmpty) {
    ret += "No device selected";
  } else {
    ret += bdname;
    ret += " ($bdaddr)";
  }
  //developer.log("get_selected_bd_summary ret $ret");
  return ret;
}

Future<void> checkConnectState() async {
  Map<String, Icon> new_icon_map = {};
  ConnectState state = await _checkUpdateSelectedDev(new_icon_map);
  if (state.index <= ConnectState.ReadyToConnect.index) {
    notifyIfMapChanged(checkStateMapIcon, new_icon_map);
  }

  // Check if USB device is selected for appropriate icon
  GnssDevice? selectedDevice = getSelectedDevice();
  bool isUsbDevice = selectedDevice != null && selectedDevice.type == DeviceConnectionType.usb;

  switch (state) {
    case ConnectState.Loading:
      floatingButtonIcon.value = Icons.access_time_rounded;
    case ConnectState.PendingRequirements:
      floatingButtonIcon.value = Icons.error_rounded;
    case ConnectState.ReadyToConnect:
      floatingButtonIcon.value = isUsbDevice ? Icons.usb_rounded : Icons.bluetooth_connected_rounded;
    case ConnectState.Connecting:
      floatingButtonIcon.value = Icons.access_time_rounded;
    case ConnectState.Connected:
      floatingButtonIcon.value = isUsbDevice ? Icons.usb_off_rounded : Icons.bluetooth_disabled_rounded;
  }
  connectState.value = state;
}

Future<ConnectState> _checkUpdateSelectedDev(
    Map<String, Icon> icon_map) async {
  try {
    // Check both Bluetooth and USB connection status
    isBtConnected.value =
        (await methodChannel.invokeMethod('is_bt_connected')) as bool? ?? false;

    // Also check USB connection status
    Map<String, dynamic> connStatus = await channels.getConnectionStatus();
    bool isUsbConnected = connStatus['usbConnected'] as bool? ?? false;
    bool isAnyConnected = isBtConnected.value || isUsbConnected;

    isNtripConnected.value =
        (await methodChannel.invokeMethod('is_ntrip_connected')) as bool? ??
            false;
    ntripPacketsCount.value =
        (await methodChannel.invokeMethod('get_ntrip_cb_count')) as int? ?? 0;
    if (isAnyConnected) {
      await wakelockEnable();
    } else {
      await wakelockDisable();
    }

    if (isAnyConnected) {
      try {
        int mockSetMillisAgo;
        DateTime now = DateTime.now();
        int nowts = now.millisecondsSinceEpoch;
        mockLocationSetStatus.value = "";
        mockSetMillisAgo = nowts - mockLocationSetTs.value;
        //developer.log("mock_location_set_ts $mock_set_ts, nowts $nowts");

        double secsAgo = mockSetMillisAgo / 1000.0;

        if (mockLocationSetTs.value == 0) {
          mockLocationSetStatus.value = "Never";
        } else {
          mockLocationSetStatus.value =
              "${secsAgo.toStringAsFixed(3)} Seconds ago";
        }
      } catch (e, trace) {
        developer.log('get parsed param exception: $e $trace');
      }
      String connTypeStr = isUsbConnected ? "Connected (USB)" : "Connected (BT)";
      connectStatus.value = connTypeStr;
      // Set isBtConnected to true for USB as well so UI shows connected state
      isBtConnected.value = true;
      return ConnectState.Connected;
    }
  } catch (e) {
    await toast("WARNING: check connection status failed: $e");
    isBtConnected.value = false;
  }

  try {
    isBtConnThreadConnecting.value =
        await methodChannel.invokeMethod('is_conn_thread_alive') as bool? ??
            false;
    /*developer.log(
        "_is_bt_conn_thread_alive_likely_connecting: ${isBtConnThreadConnecting.value}");*/
    if (isBtConnThreadConnecting.value) {
      connectStatus.value = "Connecting...";
      return ConnectState.Connecting;
    }
  } catch (e) {
    await toast("WARNING: check _is_connecting failed: $e");
    isBtConnThreadConnecting.value = false;
  }

  ConnectState ret = ConnectState.PendingRequirements;

  List<String> notGrantedPermissions = await checkPermissions();
  if (notGrantedPermissions.isNotEmpty) {
    String msg =
        "Please allow required app permissions... Re-install app if declined earlier and not seeing permission request pop-up: $notGrantedPermissions";
    icon_map["App permissions"] = iconFail;
    connectStatus.value = msg;
    return ret;
  }

  icon_map["App permissions"] = iconOk;

  // Load unified device list (BT + USB) FIRST to check device type
  await loadUnifiedDeviceList();

  // Get selected device (unified model)
  GnssDevice? selectedDevice = getSelectedDevice();
  bool isUsbDeviceSelected = selectedDevice != null && selectedDevice.type == DeviceConnectionType.usb;

  // Only require Bluetooth ON if no USB device is selected
  if (!isUsbDeviceSelected) {
    if (!(await isBluetoothOn())) {
      String msg = "Please turn ON Bluetooth...";
      icon_map["Bluetooth is OFF"] = iconFail;
      connectStatus.value = msg;
      return ret;
    }
    icon_map["Bluetooth powered ON"] = iconOk;
  } else {
    // USB device selected - Bluetooth not required
    icon_map["USB device selected (BT not required)"] = iconOk;
  }
  //developer.log('check_and_update_selected_device5');

  Map<String, String> bdMap = await getBdMap();
  notifyIfMapChanged(bdMapNotifier, bdMap);
  bdMapNotifier.value;
  //developer.log('check_and_update_selected_device6 got bdMap: $bdMap')
  String selected_dev_sum = selectedDevice?.displayName ?? "No device selected";

  // Check for QSTARZ device
  if (selectedDevice != null && selectedDevice.name.startsWith("QSTARZ")) {
    isQstarz.value = true;
  } else {
    isQstarz.value = false;
  }

  bool gapMode = prefService.get('ble_gap_scan_mode') ?? false;
  if (gapMode) {
    //bt ble gap broadcast mode
    icon_map["EcoDroidGPS-Broadcast device mode"] = iconOk;
  } else {
    //bt connect mode
    List<GnssDevice> devices = unifiedDeviceListNotifier.value;

    if (devices.isEmpty) {
      String msg =
          "Please pair a Bluetooth GPS/GNSS Receiver or connect a USB GNSS device.\n\nClick floating button to go to Bluetooth settings...";
      icon_map["No devices found"] = iconFail;
      connectStatus.value = msg;
      connectSelectedDevice.value = "No devices found...";
      return ret;
    }
    //developer.log('check_and_update_selected_device7');

    // Count device types
    int btCount = devices.where((d) => d.type == DeviceConnectionType.bluetooth).length;
    int usbCount = devices.where((d) => d.type == DeviceConnectionType.usb).length;
    String deviceSummary = "Found: $btCount BT, $usbCount USB";
    icon_map["Available devices:\n$deviceSummary"] = iconOk;

    //developer.log('check_and_update_selected_device8');

    //developer.log('check_and_update_selected_device9');

    if (selectedDevice == null) {
      String msg =
          "Please select your GPS/GNSS device in the Settings tab";

      icon_map["No device selected\n(select in settings tab)"] = iconFail;
      connectSelectedDevice.value = selected_dev_sum;
      connectStatus.value = msg;
      return ret;
    }
    icon_map["Target device selected:\n${selectedDevice.displayName}"] =
        iconOk;
    connectSelectedDevice.value = selected_dev_sum;
  }
  /* doesnt work in current targetsdk on some phones as in req emails, pixel 9?
  bool checkLocation = prefService.get('check_settings_location') ?? true;
  if (checkLocation) {
    if (!(await isLocationEnabled())) {
      String msg =
          "Location needs to be on and set to 'High Accuracy Mode' - Please go to phone Settings > Location to change this...";
      icon_map["Location must be ON and 'High Accuracy'"] = iconFail;
      connectStatus.value = msg;
      return ret;
    }
    //developer.log('check_and_update_selected_device13');
    icon_map["Location is on and 'High Accuracy'"] = iconOk;
  }*/

  if (!(await isMockLocationEnabled())) {
    String msg =
        "Please go to phone Settings > Developer Options > Under 'Debugging', set 'Mock Location app' to 'Bluetooth GNSS'...";
    icon_map["'Mock Location app' not 'Bluetooth GNSS'\n"] = iconFail;
    connectStatus.value = msg;
    return ret;
  }

  //ok - ready to connect
  icon_map["'Mock Location app' is 'Bluetooth GNSS'\nWARNING: If you want use internal GPS device again,\nSet 'Select mock location app' to 'Nothing'\n(in 'Developer Settings')."] = iconOk;
  connectStatus.value = "Please press the floating button to connect...";
  connectSelectedDevice.value = selected_dev_sum;

  return ConnectState.ReadyToConnect;
}

Future<void> setLiveArgs() async
{
  await methodChannel.invokeMethod('setLiveArgs', {
    'mock_timestamp_use_system_time': true,
    'mock_timestamp_offset_secs': double.parse(prefService.get('mock_timestamp_offset_secs') ?? "0.0"),
    'mock_lat_offset_meters': double.parse(prefService.get('mock_lat_offset_meters') ?? "0.0"),
    'mock_lon_offset_meters': double.parse(prefService.get('mock_lon_offset_meters') ?? "0.0"),
    'mock_alt_offset_meters': double.parse(prefService.get('mock_alt_offset_meters') ?? "0.0"),
  });
  developer.log("setLiveArgs setTs");
  setLiveArgsTs.value = DateTime.timestamp();
  developer.log("setLiveArgs setTs done: ${setLiveArgsTs.value}");
}