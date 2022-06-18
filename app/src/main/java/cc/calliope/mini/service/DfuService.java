package cc.calliope.mini.service;

import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import java.util.UUID;

import androidx.annotation.NonNull;
import cc.calliope.mini.BuildConfig;
import cc.calliope.mini.ui.activity.NotificationActivity;
import no.nordicsemi.android.dfu.DfuBaseService;

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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected BluetoothGatt connect(@NonNull final String address) {
        BluetoothGatt gatt = super.connect(address);

        //For Stats purpose only
        {
            BluetoothGattService deviceService = gatt.getService(DEVICE_INFORMATION_SERVICE_UUID);
            if (deviceService != null) {
                BluetoothGattCharacteristic firmwareCharacteristic = deviceService.getCharacteristic(FIRMWARE_REVISION_UUID);
                if (firmwareCharacteristic != null) {
                    gatt.readCharacteristic(firmwareCharacteristic);
                    waitFor(1000);
                    String firmware = firmwareCharacteristic.getStringValue(0);
                    logi("Firmware version String = " + firmware);
                } else {
                    logi("Error Cannot find FIRMWARE_REVISION_UUID");
                }
            } else {
                logi("Error Cannot find DEVICE_INFORMATION_SERVICE_UUID");
            }
        }//For Stats purpose only Ends

//        if (firstRun) {
//            firstRun = false;
//            BluetoothGattService fps = gatt.getService(MINI_FLASH_SERVICE_UUID);
//            if (fps == null) {
//                logi("Error Cannot find MINI_FLASH_SERVICE_UUID");
//                terminateConnection(gatt, 0);
//                return gatt;
//            }
//
//            final BluetoothGattCharacteristic sfpc1 = fps.getCharacteristic(MINI_FLASH_SERVICE_CONTROL_CHARACTERISTIC_UUID);
//            if (sfpc1 == null) {
//                logi("Error Cannot find MINI_FLASH_SERVICE_CONTROL_CHARACTERISTIC_UUID");
//                terminateConnection(gatt, 0);
//                return gatt;
//            }
//
//            sfpc1.setValue(1, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
//            try {
//                logi("Writing Flash Command ....");
//                gatt.writeCharacteristic(sfpc1);
//                waitFor(1000);
//            } catch (Exception e) {
//                e.printStackTrace();
//                loge(e.getMessage(), e);
//            }
//        }
        return gatt;
    }

    @Override
    protected boolean isDebug() {
        // Here return true if you want the service to print more logs in LogCat.
        // Library's BuildConfig in current version of Android Studio is always set to DEBUG=false, so
        // make sure you return true or your.app.BuildConfig.DEBUG here.
        return BuildConfig.DEBUG;
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
