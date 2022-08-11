package cc.calliope.mini.service;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import cc.calliope.mini.BuildConfig;
import cc.calliope.mini.R;
import cc.calliope.mini.ui.activity.NotificationActivity;
import no.nordicsemi.android.dfu.DfuBaseService;
import no.nordicsemi.android.dfu.DfuServiceInitiator;

public class DfuService extends DfuBaseService {

    private static final String TAG = "DfuService";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final UUID DEVICE_INFORMATION_SERVICE_UUID = new UUID(0x0000180A00001000L, 0x800000805F9B34FBL);
    private static final UUID FIRMWARE_REVISION_UUID = new UUID(0x00002A2600001000L, 0x800000805F9B34FBL);

    private static final UUID MINI_FLASH_SERVICE_UUID = UUID.fromString("E95D93B0-251D-470A-A062-FA1922DFA9A8");
    private static final UUID MINI_FLASH_SERVICE_CONTROL_CHARACTERISTIC_UUID = UUID.fromString("E95D93B1-251D-470A-A062-FA1922DFA9A8");

    private boolean firstRun = true;


    @Override
    protected Class<? extends Activity> getNotificationTarget() {
        return NotificationActivity.class;
    }

    @Override
    public void onCreate() {
        super.onCreate();
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
        if(flashingWithPairCode(intent) == 0){
            super.onHandleIntent(intent);
        }
    }

    public static final int PROGRESS_SERVICE_NOT_FOUND = -10;

    private int flashingWithPairCode(Intent intent) {

        final String deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);
        final String deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME);

//        sendLogBroadcast(LOG_LEVEL_VERBOSE, "Connecting to DFU target 2...");

        BluetoothGatt gatt = super.connect(deviceAddress);

        if (gatt == null)
            return 5;

        logi("Phase2 s");

//        updateProgressNotification(PROGRESS_VALIDATING);

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

        int rc = 1;

        BluetoothGattService fps = gatt.getService(MINI_FLASH_SERVICE_UUID);
        if (fps == null) {
            logi("Error Cannot find MINI_FLASH_SERVICE_UUID");
//            sendLogBroadcast(LOG_LEVEL_WARNING, "Upload aborted");
            terminateConnection(gatt, PROGRESS_SERVICE_NOT_FOUND);
            return 6;
        }

        final BluetoothGattCharacteristic sfpc1 = fps.getCharacteristic(MINI_FLASH_SERVICE_CONTROL_CHARACTERISTIC_UUID);
        if (sfpc1 == null) {
            logi("Error Cannot find MINI_FLASH_SERVICE_CONTROL_CHARACTERISTIC_UUID");
//            sendLogBroadcast(LOG_LEVEL_WARNING, "Upload aborted");
            terminateConnection(gatt, PROGRESS_SERVICE_NOT_FOUND);
            return 6;
        }

        sfpc1.setValue(1, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        try {
            logi("Writing Flash Command ....");
//            writeCharacteristic(gatt, sfpc1);
            gatt.writeCharacteristic(sfpc1);
            rc = 0;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, e.toString());
        }

        if (rc == 0) {
//            sendProgressBroadcast(PROGRESS_WAITING_REBOOT);
            //Wait for the device to reboot.
            waitUntilDisconnected();
            waitFor(1600);
//            waitUntilConnected();
            logi("Refreshing the cache before discoverServices() for Android version " + Build.VERSION.SDK_INT);
            refreshDeviceCache(gatt, true);

            /*
            do {
                logi("Calling phase 3");
//                mError = 0;
                intent = phase3(intent);
//                resultReceiver = null;
                gatt.disconnect();
                waitUntilDisconnected();
                if (mConnectionState != STATE_CLOSED) {
                    close(gatt);
                }
                gatt = null;
                logi("End phase 3");
            } while (intent != null);
            */
        }

        logi("Phase2 e");
        return rc;
    }

    @Override
    protected BluetoothGatt connect(@NonNull final String address) {
        BluetoothGatt gatt = super.connect(address);

//        //For Stats purpose only
//        {
//            BluetoothGattService deviceService = gatt.getService(DEVICE_INFORMATION_SERVICE_UUID);
//            if (deviceService != null) {
//                BluetoothGattCharacteristic firmwareCharacteristic = deviceService.getCharacteristic(FIRMWARE_REVISION_UUID);
//                if (firmwareCharacteristic != null) {
//                    gatt.readCharacteristic(firmwareCharacteristic);
//                    waitFor(1000);
//                    String firmware = firmwareCharacteristic.getStringValue(0);
<<<<<<< HEAD
//                    loge("Firmware version String = " + firmware);
//                } else {
//                    loge("Error Cannot find FIRMWARE_REVISION_UUID");
//                }
//            } else {
//                loge("Error Cannot find DEVICE_INFORMATION_SERVICE_UUID");
//            }
//        }
//        //For Stats purpose only Ends

        if (firstRun && false) { //TODO remove it
            firstRun = false;
            BluetoothGattService fps = gatt.getService(MINI_FLASH_SERVICE_UUID);
            if (fps == null) {
                loge("Error Cannot find MINI_FLASH_SERVICE_UUID");
=======
//                    logi("Firmware version String = " + firmware);
//                } else {
//                    logi("Error Cannot find FIRMWARE_REVISION_UUID");
//                }
//            } else {
//                logi("Error Cannot find DEVICE_INFORMATION_SERVICE_UUID");
//            }
//        }//For Stats purpose only Ends

        if (firstRun) {
            firstRun = false;
            BluetoothGattService fps = gatt.getService(MINI_FLASH_SERVICE_UUID);
            if (fps == null) {
                logi("Error Cannot find MINI_FLASH_SERVICE_UUID");
>>>>>>> lib_v2.0.3
                terminateConnection(gatt, 0);
                return gatt;
            }

            final BluetoothGattCharacteristic sfpc1 = fps.getCharacteristic(MINI_FLASH_SERVICE_CONTROL_CHARACTERISTIC_UUID);
            if (sfpc1 == null) {
<<<<<<< HEAD
                loge("Error Cannot find MINI_FLASH_SERVICE_CONTROL_CHARACTERISTIC_UUID");
=======
                logi("Error Cannot find MINI_FLASH_SERVICE_CONTROL_CHARACTERISTIC_UUID");
>>>>>>> lib_v2.0.3
                terminateConnection(gatt, 0);
                return gatt;
            }

            sfpc1.setValue(1, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            try {
                logi("Writing Flash Command ....");
                gatt.writeCharacteristic(sfpc1);
<<<<<<< HEAD
=======
                waitFor(1000);
>>>>>>> lib_v2.0.3
            } catch (Exception e) {
                e.printStackTrace();
                loge(e.getMessage(), e);
            }
<<<<<<< HEAD
            //Wait for the device to reboot.
            waitUntilDisconnected();
            logi("Refreshing the cache before discoverServices() for Android version " + Build.VERSION.SDK_INT);
            refreshDeviceCache(gatt, true);
        }

=======
        }
>>>>>>> lib_v2.0.3
        return gatt;
    }

    @Override
    protected boolean isDebug() {
        // Here return true if you want the service to print more logs in LogCat.
        // Library's BuildConfig in current version of Android Studio is always set to DEBUG=false, so
        // make sure you return true or your.app.BuildConfig.DEBUG here.
        return BuildConfig.DEBUG;
    }

    private void loge(final String message) {
        if (DEBUG) {
            Log.e(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    private void loge(final String message, final Throwable e) {
        if (DEBUG) {
            Log.e(TAG, "### " + Thread.currentThread().getId() + " # " + message, e);
        }
    }

    private void logi(final String message) {
        if (DEBUG) {
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }
}
