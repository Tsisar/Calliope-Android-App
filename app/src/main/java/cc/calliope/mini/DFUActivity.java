package cc.calliope.mini;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import cc.calliope.mini.adapter.ExtendedBluetoothDevice;
import cc.calliope.mini.service.DfuService;

import no.nordicsemi.android.dfu.DfuBaseService;
import no.nordicsemi.android.dfu.DfuProgressListener;
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter;
import no.nordicsemi.android.dfu.DfuServiceInitiator;
import no.nordicsemi.android.dfu.DfuServiceListenerHelper;

public class DFUActivity extends AppCompatActivity {

    private TextView deviceInfo;
    private TextView timerText;

    private static final String TAG = DFUActivity.class.getSimpleName();

    private final DfuProgressListener mDfuProgressListener = new DfuProgressListenerAdapter() {
        @Override
        public void onDeviceConnecting(final String deviceAddress) {
            timerText.setText("Device Connecting");
//            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
//            Log.e(TAG, method);
        }

        @Override
        public void onDfuProcessStarting(final String deviceAddress) {
            timerText.setText("Process Starting");
//            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
//            Log.e(TAG, method);
        }

        @Override
        public void onEnablingDfuMode(final String deviceAddress) {
            timerText.setText("Enabling Dfu Mode");
//            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
//            Log.e(TAG, method);
        }

        @Override
        public void onFirmwareValidating(final String deviceAddress) {
            timerText.setText("Firmware Validating");
//            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
//            Log.e(TAG, method);
        }

        @Override
        public void onDeviceDisconnecting(final String deviceAddress) {
            timerText.setText("Device Disconnecting");
//            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
//            Log.e(TAG, method);
        }

        @Override
        public void onDfuCompleted(final String deviceAddress) {
            timerText.setText("Dfu Completed");
            finish();
//            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
//            Log.e(TAG, method);
        }

        @Override
        public void onDfuAborted(final String deviceAddress) {
            timerText.setText("Dfu Aborted");
//            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
//            Log.e(TAG, method);
        }

        @Override
        public void onProgressChanged(final String deviceAddress, final int percent, final float speed, final float avgSpeed, final int currentPart, final int partsTotal) {
            deviceInfo.setText(percent + "%");
            timerText.setText(R.string.info_text_uploading);

//            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
//            Log.e(TAG, method + " percent: " + percent);
        }

        @Override
        public void onError(final String deviceAddress, final int error, final int errorType, final String message) {
            deviceInfo.setText("ERROR:");
            timerText.setText(message);
            String method = Thread.currentThread().getStackTrace()[2].getMethodName();
            Log.e(TAG, method + " error: " + message);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dfu);

        deviceInfo = findViewById(R.id.statusInfo);
        timerText = findViewById(R.id.timerText);

        timerText.setText("Device Connecting");

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
        Log.i("DFUExtra", "ForegroundPermissionGranted: " + foregroundPermissionGranted());

        final DfuServiceInitiator starter = new DfuServiceInitiator(device.getAddress())
                .setDeviceName(device.getName())
                .setMbrSize(0x18000) //TODO Modify HexInputStream
                //TODO  Android Oreo or newer may kill a background service few moments after user closes the application.
                // Consider enabling foreground service
                // Bud some time we have exception:
                // android.app.ForegroundServiceDidNotStartInTimeException: Context.startForegroundService() did not then call Service.startForeground()
                .setForeground(false)
//                .setForeground(foregroundPermissionGranted())
                .setNumberOfRetries(3)
                .setRebootTime(1000)
                .setKeepBond(false);

        //TODO
        // Reboot after fw download

        starter.setBinOrHex(DfuBaseService.TYPE_APPLICATION, file);
        starter.start(this, DfuService.class);
    }

    private boolean foregroundPermissionGranted(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED;
        }
        return false;
    }

}