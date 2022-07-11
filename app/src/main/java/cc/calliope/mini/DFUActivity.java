package cc.calliope.mini;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import cc.calliope.mini.adapter.ExtendedBluetoothDevice;
import cc.calliope.mini.service.DfuService;

import no.nordicsemi.android.dfu.DfuBaseService;
import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;
import no.nordicsemi.android.dfu.DfuSettingsConstants;

import static no.nordicsemi.android.dfu.DfuBaseService.NOTIFICATION_CHANNEL_DFU;

public class DFUActivity extends AppCompatActivity {

    private static final String TAG = DFUActivity.class.getSimpleName();

    private final DfuProgressListener mDfuProgressListener = new DfuProgressListenerAdapter() {
        @Override
        public void onDeviceConnecting(final String deviceAddress) {
            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
            Log.w(TAG, method);
        }

        @Override
        public void onDfuProcessStarting(final String deviceAddress) {
            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
            Log.w(TAG, method);
        }

        @Override
        public void onEnablingDfuMode(final String deviceAddress) {
            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
            Log.w(TAG, method);
        }

        @Override
        public void onFirmwareValidating(final String deviceAddress) {
            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
            Log.w(TAG, method);
        }

        @Override
        public void onDeviceDisconnecting(final String deviceAddress) {
            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
            Log.w(TAG, method);
        }

        @Override
        public void onDfuCompleted(final String deviceAddress) {
            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
            Log.w(TAG, method);

        }

        @Override
        public void onDfuAborted(final String deviceAddress) {
            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
            Log.w(TAG, method);
        }

        @Override
        public void onProgressChanged(final String deviceAddress, final int percent, final float speed, final float avgSpeed, final int currentPart, final int partsTotal) {
            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
            Log.w(TAG, method + " percent: " + percent);
        }

        @Override
        public void onError(final String deviceAddress, final int error, final int errorType, final String message) {
            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
            Log.e(TAG, method + " error: " + message);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dfu);

        initiateFlashing();
    }

    @Override
    public void onResume() {
        super.onResume();

        DfuServiceListenerHelper.registerProgressListener(this, mDfuProgressListener);
    }

    @Override
    public void onPause() {
        super.onPause();

        DfuServiceListenerHelper.unregisterProgressListener(this, mDfuProgressListener);
    }



    /**
     * Prepares for flashing process.
     * <p/>
     * <p>>Unregisters DFU receiver, sets activity state to the find device state,
     * registers callbacks requisite for flashing and starts flashing.</p>
     */
    protected void initiateFlashing() {
        startFlashing();
    }

    /**
     * Creates and starts service to flash a program to a micro:bit board.
     */
    protected void startFlashing() {
        final Intent intent = getIntent();
        final ExtendedBluetoothDevice device = intent.getParcelableExtra("cc.calliope.mini.EXTRA_DEVICE");

        Bundle extras = intent.getExtras();
        final String file = extras.getString("EXTRA_FILE");

        Log.i("DFUExtra", "mAddress: " + device.getAddress());
        Log.i("DFUExtra", "mPattern: " + device.getName());
        Log.i("DFUExtra", "mPairingCode: " + 0);
        Log.i("DFUExtra", "MIME_TYPE_OCTET_STREAM: " + DfuService.MIME_TYPE_OCTET_STREAM);
        Log.i("DFUExtra", "filePath: " + file);

        Log.i("DFUExtra", "Start Flashing");


        final DfuServiceInitiator starter = new DfuServiceInitiator(device.getAddress())
                .setDeviceName(device.getName())
                .setMbrSize(0x18000)
//                .setForeground(checkForegroundPermission())
//                .setForeground(false)
                .setKeepBond(false);

        starter.setBinOrHex(DfuBaseService.TYPE_APPLICATION, file);

        starter.start(this, DfuService.class);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel(NotificationManager notificationManager){
        String channelName = "My Foreground Service";
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_DFU, channelName, NotificationManager.IMPORTANCE_HIGH);
        // omitted the LED color
        channel.setImportance(NotificationManager.IMPORTANCE_NONE);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        notificationManager.createNotificationChannel(channel);
    }

    private boolean checkForegroundPermission(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "FOREGROUND_SERVICE permission denied");
            return false;
        }
        Log.e(TAG, "FOREGROUND_SERVICE permission allowed");
        return true;
    }

}