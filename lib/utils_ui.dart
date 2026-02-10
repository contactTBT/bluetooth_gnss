import 'dart:developer' as developer;

import 'package:flutter/material.dart';
import 'package:pref/pref.dart';

import 'channels.dart';
import 'connect.dart';
import 'gnss_device.dart';

const double defaultChecklistIconSize = 35;
const double defaultConnectStateIconSize = 60;

const iconNotConnected = Icon(
  Icons.bluetooth_disabled,
  color: Colors.red,
  size: defaultConnectStateIconSize,
);
const iconBluetoothSettings = Icon(
  Icons.settings_bluetooth,
  color: Colors.white,
);
const iconConnect = Icon(
  Icons.bluetooth_connected,
  color: Colors.white,
);
const iconConnecting = Icon(
  Icons.bluetooth_connected,
  color: Colors.white,
);
const iconConnected = Icon(
  Icons.bluetooth_connected,
  color: Colors.blue,
  size: defaultConnectStateIconSize,
);
const iconConnectedUsb = Icon(
  Icons.usb,
  color: Colors.green,
  size: defaultConnectStateIconSize,
);
const iconLoading = Icon(
  Icons.access_time,
  color: Colors.grey,
  size: defaultConnectStateIconSize,
);
const iconOk = Icon(
  Icons.check_circle,
  color: Colors.lightBlueAccent,
  size: defaultChecklistIconSize,
);
const iconFail = Icon(
  Icons.cancel,
  color: Colors.blueGrey,
  size: defaultChecklistIconSize,
);
const iconWarn = Icon(
  Icons.warning_amber_rounded,
  color: Colors.orange,
  size: defaultChecklistIconSize,
);

/// Returns the appropriate connected icon based on device type
Icon getConnectedIcon() {
  GnssDevice? selectedDevice = getSelectedDevice();
  if (selectedDevice != null && selectedDevice.type == DeviceConnectionType.usb) {
    return iconConnectedUsb;
  }
  return iconConnected;
}

Future<void> toast(String msg) async {
  try {
    await methodChannel.invokeMethod("toast", {"msg": msg});
    developer.log("toast: $msg");
  } catch (e) {
    print("WARNING: toast failed exception: $e");
  }
}

void snackbar(BuildContext context, String msg) {
  try {
    final snackBar = SnackBar(content: Text(msg));
    ScaffoldMessenger.of(context).showSnackBar(snackBar);
  } catch (e) {
    print("WARNING: snackbar failed exception: $e");
  }
}

List<Widget> paramRowList(BuildContext context, List<List<String>> m)
{
  return m.map((me) => paramRow(context, me[1], title: me[0])).toList() as List<Widget>;
}

DateTime tsToDateTime(int ts_millis) {
  DateTime dateTime =
  DateTime.fromMillisecondsSinceEpoch(ts_millis);
  return dateTime;
}

String tsToDateTimeStr(int ts_millis)
{
  DateTime dateTime = tsToDateTime(ts_millis);
  return "${dateTime.year}-${dateTime.month.toString().padLeft(2, '0')}-${dateTime.day.toString().padLeft(2, '0')} "
      "${dateTime.hour.toString().padLeft(2, '0')}:${dateTime.minute.toString().padLeft(2, '0')}:${dateTime.second.toString().padLeft(2, '0')}.${dateTime.millisecond.toString().padLeft(3, '0')}";
}

String? validateDouble(String? newValue) {
  double? tsoffset = double.tryParse(newValue ?? "");
  if (tsoffset == null) {
    return "Numbers with fractions only";
  }
  return null;
}

String? validateDoublePositive(String? newValue) {
  double? tsoffset = double.tryParse(newValue ?? "");
  if (tsoffset == null) {
    return "Numbers with fractions only";
  }
  if (tsoffset <= 0) {
    return "Greater than 0 only";
  }
  return null;
}

Row paramRow(BuildContext context, String param, {TextStyle? style, int double_fraction_digits=2, String title=""}) {
  paramMapSubscribe(param);
  return Row(
    mainAxisAlignment: MainAxisAlignment.spaceBetween,
    children: <Widget>[
      Text('${title.isEmpty?param.toUpperCase():title}',
          style: style),
      ValueListenableBuilder<dynamic>(
        valueListenable: paramMap[param]!,
        builder: (context, value, child) {
          if (value is double) {
            if (param.endsWith("_lat") || param.endsWith("_lon")) {
              double_fraction_digits = 7;
            }
            value = value.toStringAsFixed(double_fraction_digits);
          } else if (param.endsWith("_ts") && value is int) {
            value = tsToDateTimeStr(value);
          }
          //developer.log("paramRow updated - param: $param value: $value");
          return Text(
          '$value',
          style: style,
          );
        },
      ),
    ],
  );
}

/// Builds a reactive [Text] widget from a [ValueNotifier<String>].
Widget reactiveText(
  ValueNotifier<dynamic> value, {
  TextStyle? style,
  TextAlign? textAlign,
  int? maxLines,
}) {
  return ValueListenableBuilder<dynamic>(
    valueListenable: value,
    builder: (_, val, __) => Text(
      "$val",
      style: style,
      textAlign: textAlign,
      maxLines: maxLines,
    ),
  );
}

/// Builds a reactive [Icon] widget from a [ValueNotifier<IconData>].
Widget reactiveIcon(
  ValueNotifier<IconData> value, {
  double? size,
  Color? color,
}) {
  return ValueListenableBuilder<IconData>(
    valueListenable: value,
    builder: (_, val, __) => Icon(val, size: size, color: color),
  );
}

Widget reactiveIconMapCard(ValueNotifier<Map<String, Icon>> value) {
  return ValueListenableBuilder<Map<String, Icon>>(
    valueListenable: value,
    builder: (BuildContext context, Map<String, Icon> _checkStateMapIcon,
        Widget? child) {
      developer.log("reactiveIconMapCard build");
      List<Widget> checklist = [];
      for (String key in _checkStateMapIcon.keys) {
        Row row = Row(mainAxisAlignment: MainAxisAlignment.start, children: [
          _checkStateMapIcon[key]!,
          Container(
              padding: const EdgeInsets.all(8.0),
              child: Text(
                key,
                style: Theme.of(context).textTheme.bodySmall,
              )),
        ]);
        checklist.add(row);
      }

      return Card(
          child: Container(
        padding: const EdgeInsets.all(10.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.start,
          children: checklist,
        ),
      ));
    },
  );
}

/// Builds a reactive [Visibility] widget from a [ValueNotifier<bool>].
Widget reactiveVisibility(
  ValueNotifier<bool> value, {
  required Widget child,
}) {
  return ValueListenableBuilder<bool>(
    valueListenable: value,
    builder: (_, visible, __) => Visibility(visible: visible, child: child),
  );
}

Widget reactivePrefDropDown(String pref_key, String title,
    ValueNotifier<Map<String, String>> bdaddr_to_name_map) {
  return ValueListenableBuilder<Map<String, String>>(
    valueListenable: bdaddr_to_name_map,
    builder: (BuildContext context, Map<String, String> bdaddr_to_name_map,
        Widget? child) {
      developer.log("reactivePrefDropDown build");
      List<DropdownMenuItem<String>> devlist = List.empty(growable: true);
      for (MapEntry<String, String> entry in bdaddr_to_name_map.entries) {
        devlist
            .add(DropdownMenuItem(value: entry.key, child: Text(entry.value)));
      }
      return PrefDropdown(
          title: Text(title),
          items: devlist,
          pref: pref_key);
    },
  );
}

/// Builds a unified device dropdown showing both Bluetooth and USB devices
Widget reactiveUnifiedDeviceDropDown({
  required String title,
  required String emptyHint,
}) {
  return ValueListenableBuilder<List<GnssDevice>>(
    valueListenable: unifiedDeviceListNotifier,
    builder: (BuildContext context, List<GnssDevice> devices, Widget? child) {
      developer.log("reactiveUnifiedDeviceDropDown build: ${devices.length} devices");

      // Get currently selected device
      GnssDevice? selectedDevice = getSelectedDevice();
      String? selectedValue = selectedDevice?.id;

      // Build dropdown items
      List<DropdownMenuItem<String>> items = [];
      for (GnssDevice device in devices) {
        items.add(DropdownMenuItem(
          value: device.id,
          child: Row(
            children: [
              Icon(
                device.type == DeviceConnectionType.bluetooth
                    ? Icons.bluetooth
                    : Icons.usb,
                color: device.type == DeviceConnectionType.bluetooth
                    ? Colors.blue
                    : Colors.green,
                size: 20,
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      device.name,
                      overflow: TextOverflow.ellipsis,
                    ),
                    Text(
                      device.subtitle,
                      style: const TextStyle(fontSize: 10, color: Colors.grey),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
            ],
          ),
        ));
      }

      if (items.isEmpty) {
        return ListTile(
          title: Text(title),
          subtitle: Text(emptyHint),
          leading: const Icon(Icons.warning_amber, color: Colors.orange),
        );
      }

      // Validate selection - reset if selected device not in list
      if (selectedValue != null) {
        bool found = devices.any((d) => d.id == selectedValue);
        if (!found) {
          selectedValue = null;
        }
      }

      return ListTile(
        title: Text(title),
        subtitle: DropdownButton<String>(
          value: selectedValue,
          hint: Text(emptyHint),
          isExpanded: true,
          items: items,
          onChanged: (String? newValue) async {
            if (newValue == null) {
              await setSelectedDevice(null);
              return;
            }
            // Find selected device
            GnssDevice? device;
            for (var d in devices) {
              if (d.id == newValue) {
                device = d;
                break;
              }
            }
            await setSelectedDevice(device);
          },
        ),
      );
    },
  );
}

/// Refreshes the unified device list (BT + USB)
Future<void> refreshUnifiedDeviceList() async {
  await loadUnifiedDeviceList();
}
