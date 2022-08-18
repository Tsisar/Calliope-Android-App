package cc.calliope.mini.service;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.util.UUID;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import cc.calliope.mini.BuildConfig;
import cc.calliope.mini.ui.activity.NotificationActivity;
import no.nordicsemi.android.dfu.DfuBaseService;
import no.nordicsemi.android.dfu.DfuServiceInitiator;

public class DfuService extends DfuBaseService {

    private static final String TAG = "DfuService";
    private static final boolean DEBUG = BuildConfig.DEBUG;

//    private static final UUID DEVICE_INFORMATION_SERVICE_UUID = new UUID(0x0000180A00001000L, 0x800000805F9B34FBL);
//    private static final UUID FIRMWARE_REVISION_UUID = new UUID(0x00002A2600001000L, 0x800000805F9B34FBL);

    private static final UUID MINI_FLASH_SERVICE_UUID = UUID.fromString("E95D93B0-251D-470A-A062-FA1922DFA9A8");
    private static final UUID MINI_FLASH_SERVICE_CONTROL_CHARACTERISTIC_UUID = UUID.fromString("E95D93B1-251D-470A-A062-FA1922DFA9A8");

//    private LocalBroadcastManager mBroadcastManager;

    @Override
    protected Class<? extends Activity> getNotificationTarget() {
        return NotificationActivity.class;
    }

    @Override
    public void onCreate() {
        super.onCreate();
//        mBroadcastManager = LocalBroadcastManager.getInstance(this);

        // Enable Notification Channel for Android OREO
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            DfuServiceInitiator.createDfuNotificationChannel(getApplicationContext());
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onHandleIntent(@Nullable final Intent intent) {
        assert intent != null;
        if (!flashingWithPairCode(intent)) {
            loge("The calliope over the air firmware update not Initialized");
//            abort();
        }
        super.onHandleIntent(intent);
    }

    public static final int PROGRESS_SERVICE_NOT_FOUND = -10;

    private boolean flashingWithPairCode(Intent intent) {
        final String deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);
        final long delay = intent.getLongExtra(DfuBaseService.EXTRA_SCAN_DELAY, 0);

        BluetoothGatt gatt = super.connect(deviceAddress);
        if (gatt == null) {
            logw("Bluetooth adapter disabled");
            return false;
        }

//        //For Stats purpose only
//        {
//            BluetoothGattService deviceService = gatt.getService(DEVICE_INFORMATION_SERVICE_UUID);
//            if (deviceService != null) {
//                BluetoothGattCharacteristic firmwareCharacteristic = deviceService.getCharacteristic(FIRMWARE_REVISION_UUID);
//                if (firmwareCharacteristic != null) {
//                    String firmware = null;
//                    firmware = readCharacteristicNoFailure(gatt, firmwareCharacteristic);
//                    logi("Firmware version String = " + firmware);
//                    sendStatsMiniFirmware(firmware);
//                } else {
//                    logi("Error Cannot find FIRMWARE_REVISION_UUID");
//                }
//            } else {
//                logi("Error Cannot find DEVICE_INFORMATION_SERVICE_UUID");
//            }
//        }//For Stats purpose only Ends

        BluetoothGattService flashService = gatt.getService(MINI_FLASH_SERVICE_UUID);
        if (flashService == null) {
            logw("Cannot find MINI_FLASH_SERVICE_UUID");
            terminateConnection(gatt, PROGRESS_SERVICE_NOT_FOUND);
            return false;
        }

        final BluetoothGattCharacteristic flashServiceCharacteristic = flashService.getCharacteristic(MINI_FLASH_SERVICE_CONTROL_CHARACTERISTIC_UUID);
        if (flashServiceCharacteristic == null) {
            logw("Cannot find MINI_FLASH_SERVICE_CONTROL_CHARACTERISTIC_UUID");
            terminateConnection(gatt, PROGRESS_SERVICE_NOT_FOUND);
            return false;
        }

        flashServiceCharacteristic.setValue(1, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        try {
            logi("Writing Flash Command...");
//            writeCharacteristic(gatt, sfpc1);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                logw("Permission BLUETOOTH_CONNECT not granted");
            }
            gatt.writeCharacteristic(flashServiceCharacteristic);
        } catch (Exception e) {
            e.printStackTrace();
            loge(e.toString());
            terminateConnection(gatt, PROGRESS_SERVICE_NOT_FOUND);
            return false;
        }

        //Wait for the device to reboot.
        logi("Wait for the device to reboot");
        waitUntilDisconnected();
        waitFor(delay);

        logi("Refreshing the cache before discoverServices() for Android version " + Build.VERSION.SDK_INT);
        refreshDeviceCache(gatt, true);

        return true;
    }


    @Override
    protected boolean isDebug() {
        // Here return true if you want the service to print more logs in LogCat.
        // Library's BuildConfig in current version of Android Studio is always set to DEBUG=false, so
        // make sure you return true or your.app.BuildConfig.DEBUG here.
        return DEBUG;
    }

//    public void abort() {
//        final Intent pauseAction = new Intent(DfuBaseService.BROADCAST_ACTION);
//        pauseAction.putExtra(DfuBaseService.EXTRA_ACTION, DfuBaseService.ACTION_ABORT);
//        mBroadcastManager.sendBroadcast(pauseAction);
//    }

    private void loge(final String message) {
        if (isDebug()) {
            Log.e(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    private void logw(final String message) {
        if (isDebug()) {
            Log.w(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    private void logi(final String message) {
        if (isDebug()) {
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }
}
