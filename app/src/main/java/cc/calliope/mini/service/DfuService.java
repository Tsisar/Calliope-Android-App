package cc.calliope.mini.service;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import cc.calliope.mini.BuildConfig;
import cc.calliope.mini.ui.activity.NotificationActivity;
import no.nordicsemi.android.dfu.DfuBaseService;
import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.HexFileValidationException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;

public class DfuService extends DfuBaseService {

    private static final String TAG = "DfuService";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    /**
     * The current connection state. If its value is > 0 than an error has occurred.
     * Error number is a negative value of mConnectionState
     */
    protected int mConnectionState;
    protected final static int STATE_DISCONNECTED = 0;
    protected final static int STATE_CONNECTING = -1;
    protected final static int STATE_CONNECTED = -2;
    protected final static int STATE_CONNECTED_AND_READY = -3; // indicates that services were discovered
    protected final static int STATE_DISCONNECTING = -4;
    protected final static int STATE_CLOSED = -5;

    /**
     * Lock used in synchronization purposes
     */
    private final Object mLock = new Object();
    private String mDeviceAddress;

    /**
     * Flag indicating whether the request was completed or not
     */
    private boolean mRequestCompleted;

    /**
     * The number of the last error that has occurred or 0 if there was no error
     */
    private volatile int mError = 0;

    /** Flag set to true if sending was aborted. */
    private boolean mAborted;
    private boolean mPaused;

    /**
     * Flag set when we got confirmation from the device that Service Changed indications are enabled.
     */
    private boolean mServiceChangedIndicationsEnabled;
    /**
     * Flag set when we got confirmation from the device that notifications are enabled.
     */
    private boolean mNotificationsEnabled;
    /**
     * Flag indicating whether the image size has been already transferred or not
     */
    private boolean mImageSizeSent;
    /**
     * Flag indicating whether the init packet has been already transferred or not
     */
    private boolean mInitPacketSent;
    /**
     * Number of bytes transmitted.
     */
    private int mBytesSent;

    /**
     * Number of bytes confirmed by the notification.
     */
    @SuppressWarnings("unused")
    private int mBytesConfirmed;
    private int mPacketsSentSinceNotification;
    /**
     * The number of packets of firmware data to be send before receiving a new Packets receipt notification. 0 disables the packets notifications
     */
    private int mPacketsBeforeNotification = 10;

    /**
     * Size of BIN content of all hex files that are going to be transmitted.
     */
    private int mImageSizeInBytes;

    private boolean mRemoteErrorOccurred;
    /**
     * Flag sent when a request has been sent that will cause the DFU target to reset. Often, after sending such command, Android throws a connection state error. If this flag is set the error will be
     * ignored.
     */
    private boolean mResetRequestSent;

    private static final int MAX_PACKET_SIZE = 20; // the maximum number of bytes in one packet is 20. May be less.
    private final byte[] mBuffer = new byte[MAX_PACKET_SIZE];

    private InputStream mInputStream;

    /**
     * Latest data received from device using notification.
     */
    private byte[] mReceivedData = null;

    private static final int OP_CODE_RESPONSE_CODE_KEY = 0x10; // 16
    private static final int OP_CODE_PACKET_RECEIPT_NOTIF_KEY = 0x11; // 11

    public static final int DFU_STATUS_SUCCESS = 1;

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    // UUIDs used by the DFU
    private static final UUID GENERIC_ATTRIBUTE_SERVICE_UUID = new UUID(0x0000180100001000L, 0x800000805F9B34FBL);
    private static final UUID SERVICE_CHANGED_UUID = new UUID(0x00002A0500001000L, 0x800000805F9B34FBL);

    private static final UUID DFU_SERVICE_UUID = new UUID(0x000015301212EFDEL, 0x1523785FEABCD123L);
    private static final UUID DFU_CONTROL_POINT_UUID = new UUID(0x000015311212EFDEL, 0x1523785FEABCD123L);
    private static final UUID DFU_PACKET_UUID = new UUID(0x000015321212EFDEL, 0x1523785FEABCD123L);

    private static final UUID DFU_VERSION = new UUID(0x000015341212EFDEL, 0x1523785FEABCD123L);
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = new UUID(0x0000290200001000L, 0x800000805F9B34FBL);

    private static final UUID DEVICE_INFORMATION_SERVICE_UUID = new UUID(0x0000180A00001000L, 0x800000805F9B34FBL);
    private static final UUID FIRMWARE_REVISION_UUID = new UUID(0x00002A2600001000L, 0x800000805F9B34FBL);

    private static final UUID MINI_FLASH_SERVICE_UUID = UUID.fromString("E95D93B0-251D-470A-A062-FA1922DFA9A8");
    private static final UUID MINI_FLASH_SERVICE_CONTROL_CHARACTERISTIC_UUID = UUID.fromString("E95D93B1-251D-470A-A062-FA1922DFA9A8");
    private BluetoothGatt gatt;

    private boolean firstRun = true;

    private final BroadcastReceiver mBondStateBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            // Obtain the device and check it this is the one that we are connected to
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (!device.getAddress().equals(mDeviceAddress))
                return;

            // Read bond state
            final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
            if (bondState == BluetoothDevice.BOND_BONDING)
                return;

            mRequestCompleted = true;

            // Notify waiting thread
            synchronized (mLock) {
                mLock.notifyAll();
            }
        }
    };

    private final BroadcastReceiver mDfuActionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final int action = intent.getIntExtra(EXTRA_ACTION, 0);

            switch (action) {
                case ACTION_PAUSE:
                    mPaused = true;
                    break;

                case ACTION_RESUME:
                    mPaused = false;

                    // Notify waiting thread
                    synchronized (mLock) {
                        mLock.notifyAll();
                    }
                    break;

                case ACTION_ABORT:
                    mPaused = false;
                    mAborted = true;

                    // Notify waiting thread
                    synchronized (mLock) {
                        mLock.notifyAll();
                    }
                    break;
            }
        }
    };

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            // Check whether an error occurred
            logi("onConnectionStateChange() :: Start");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    logi("onConnectionStateChange() :: Connected to GATT server");
                    mConnectionState = STATE_CONNECTED;

                    /*
                     *  The onConnectionStateChange callback is called just after establishing connection and before sending Encryption Request BLE event in case of a paired device.
                     *  In that case and when the Service Changed CCCD is enabled we will get the indication after initializing the encryption, about 1600 milliseconds later.
                     *  If we discover services right after connecting, the onServicesDiscovered callback will be called immediately, before receiving the indication and the following
                     *  service discovery and we may end up with old, application's services instead.
                     *
                     *  This is to support the buttonless switch from application to bootloader mode where the DFU bootloader notifies the master about service change.
                     *  Tested on Nexus 4 (Android 4.4.4 and 5), Nexus 5 (Android 5), Samsung Note 2 (Android 4.4.2). The time after connection to end of service discovery is about 1.6s
                     *  on Samsung Note 2.
                     *
                     *  NOTE: We are doing this to avoid the hack with calling the hidden gatt.refresh() method, at least for bonded devices.
                     */
                    if (gatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED) {
                        try {
                            synchronized (this) {
                                logd("onConnectionStateChange() :: Waiting 1600 ms for a possible Service Changed indication...");
                                wait(1600);

                                // After 1.6s the services are already discovered so the following gatt.discoverServices() finishes almost immediately.

                                // NOTE: This also works with shorted waiting time. The gatt.discoverServices() must be called after the indication is received which is
                                // about 600ms after establishing connection. Values 600 - 1600ms should be OK.
                            }
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.toString());
                            // Do nothing
                        }
                    }

                    // Attempts to discover services after successful connection.
                    final boolean success = gatt.discoverServices();
                    logi("onConnectionStateChange() :: Attempting to start service discovery... " + (success ? "succeed" : "failed"));

                    if (!success) {
                        mError = ERROR_SERVICE_DISCOVERY_NOT_STARTED;
                    } else {
                        // Just return here, lock will be notified when service discovery finishes
                        return;
                    }
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    logi("onConnectionStateChange() :: Disconnected from GATT server");
                    mPaused = false;
                    mConnectionState = STATE_DISCONNECTED;
                }
            } else {
                loge("Connection state change error: " + status + " newState: " + newState);
/*				if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    mConnectionState = STATE_DISCONNECTED;
                    if (mServicePhase == PAIRING_REQUEST ){
                        mServicePhase = PAIRING_FAILED ;
                        updateProgressNotification(status);
                    }
                }*/
                mPaused = false;
                mError = ERROR_CONNECTION_STATE_MASK | status;
            }

            // Notify waiting thread
            synchronized (mLock) {
                mLock.notifyAll();
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            // Notify waiting thread
            logi("onServicesDiscovered() :: Start");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                logi("onServicesDiscovered() :: Services discovered");
                mConnectionState = STATE_CONNECTED_AND_READY;
            } else {
                loge("onServicesDiscovered() :: Service discovery error: " + status);
                mError = ERROR_CONNECTION_MASK | status;
            }
            synchronized (mLock) {
                mLock.notifyAll();
            }
        }

        @Override
        public void onDescriptorRead(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (CLIENT_CHARACTERISTIC_CONFIG.equals(descriptor.getUuid())) {
                    if (SERVICE_CHANGED_UUID.equals(descriptor.getCharacteristic().getUuid())) {
                        // We have enabled indications for the Service Changed characteristic
                        mServiceChangedIndicationsEnabled = descriptor.getValue()[0] == 2;
                        mRequestCompleted = true;
                    }
                }
            } else {
                loge("Descriptor read error: " + status);
                mError = ERROR_CONNECTION_MASK | status;
            }
            // Notify waiting thread
            synchronized (mLock) {
                mLock.notifyAll();
            }
        }

        @Override
        public void onDescriptorWrite(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (CLIENT_CHARACTERISTIC_CONFIG.equals(descriptor.getUuid())) {
                    if (SERVICE_CHANGED_UUID.equals(descriptor.getCharacteristic().getUuid())) {
                        // We have enabled indications for the Service Changed characteristic
                        mServiceChangedIndicationsEnabled = descriptor.getValue()[0] == 2;
                    } else {
                        // We have enabled notifications for this characteristic
                        mNotificationsEnabled = descriptor.getValue()[0] == 1;
                    }
                }
            } else {
                loge("Descriptor write error: " + status);
                mError = ERROR_CONNECTION_MASK | status;
            }
            // Notify waiting thread
            synchronized (mLock) {
                mLock.notifyAll();
            }
        }

        @Override
        public void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                /*
                 * This method is called when either a CONTROL POINT or PACKET characteristic has been written.
                 * If it is the CONTROL POINT characteristic, just set the {@link mRequestCompleted} flag to true. The main thread will continue its task when notified.
                 * If the PACKET characteristic was written we must:
                 * - if the image size was written in DFU Start procedure, just set flag to true
                 * otherwise
                 * - send the next packet, if notification is not required at that moment, or
                 * - do nothing, because we have to wait for the notification to confirm the data received
                 */
                if (DFU_PACKET_UUID.equals(characteristic.getUuid())) {
                    if (mImageSizeSent && mInitPacketSent) {
                        // If the PACKET characteristic was written with image data, update counters
                        mBytesSent += characteristic.getValue().length;
                        mPacketsSentSinceNotification++;

                        // If a packet receipt notification is expected, or the last packet was sent, do nothing. There onCharacteristicChanged listener will catch either
                        // a packet confirmation (if there are more bytes to send) or the image received notification (it upload process was completed)
                        final boolean notificationExpected = mPacketsBeforeNotification > 0 && mPacketsSentSinceNotification == mPacketsBeforeNotification;
                        final boolean lastPacketTransferred = mBytesSent == mImageSizeInBytes;

                        if (notificationExpected || lastPacketTransferred)
                            return;

                        // When neither of them is true, send the next packet
                        try {
                            waitIfPaused();
                            // The writing might have been aborted (mAborted = true), an error might have occurred.
                            // In that case stop sending.
                            if (mAborted || mError != 0 || mRemoteErrorOccurred || mResetRequestSent) {
                                // notify waiting thread
                                synchronized (mLock) {
                                    sendLogBroadcast(LOG_LEVEL_WARNING, "Upload terminated");
                                    mLock.notifyAll();
                                    return;
                                }
                            }

                            final byte[] buffer = mBuffer;
                            final int size = mInputStream.read(buffer);
                            writePacket(gatt, characteristic, buffer, size);
                            updateProgressNotification();
                            return;
                        } catch (final HexFileValidationException e) {
                            loge("Invalid HEX file");
                            mError = ERROR_FILE_INVALID;
                        } catch (final IOException e) {
                            loge("Error while reading the input stream", e);
                            mError = ERROR_FILE_IO_EXCEPTION;
                        }
                    } else if (!mImageSizeSent) {
                        // We've got confirmation that the image size was sent
                        sendLogBroadcast(LOG_LEVEL_INFO, "Data written to " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
                        mImageSizeSent = true;
                    } else {
                        // We've got confirmation that the init packet was sent
                        sendLogBroadcast(LOG_LEVEL_INFO, "Data written to " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
                        mInitPacketSent = true;
                    }
                } else {
                    // If the CONTROL POINT characteristic was written just set the flag to true. The main thread will continue its task when notified.
                    sendLogBroadcast(LOG_LEVEL_INFO, "Data written to " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
                    mRequestCompleted = true;
                }
            } else {
                /*
                 * If a Reset (Op Code = 6) or Activate and Reset (Op Code = 5) commands are sent, the DFU target resets and sometimes does it so quickly that does not manage to send
                 * any ACK to the controller and error 133 is thrown here. This bug should be fixed in SDK 8.0+ where the target would gracefully disconnect before restarting.
                 */
                if (mResetRequestSent)
                    mRequestCompleted = true;
                else {
                    loge("Characteristic write error: " + status);
                    mError = ERROR_CONNECTION_MASK | status;
                }
            }

            // Notify waiting thread
            synchronized (mLock) {
                mLock.notifyAll();
            }
        }

        @Override
        public void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                /*
                 * This method is called when the DFU Version characteristic has been read.
                 */
                sendLogBroadcast(LOG_LEVEL_INFO, "Read Response received from " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
                mReceivedData = characteristic.getValue();
                mRequestCompleted = true;
            } else {
                loge("Characteristic read error: " + status);
                mError = ERROR_CONNECTION_MASK | status;
            }

            // Notify waiting thread
            synchronized (mLock) {
                mLock.notifyAll();
            }
        }

        @Override
        public void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            final int responseType = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            switch (responseType) {
                case OP_CODE_PACKET_RECEIPT_NOTIF_KEY:
                    final BluetoothGattCharacteristic packetCharacteristic = gatt.getService(DFU_SERVICE_UUID).getCharacteristic(DFU_PACKET_UUID);

                    try {
                        mBytesConfirmed = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 1);
                        mPacketsSentSinceNotification = 0;

                        waitIfPaused();
                        // The writing might have been aborted (mAborted = true), an error might have occurred.
                        // In that case quit sending.
                        if (mAborted || mError != 0 || mRemoteErrorOccurred || mResetRequestSent) {
                            sendLogBroadcast(LOG_LEVEL_WARNING, "Upload terminated");
                            break;
                        }

                        final byte[] buffer = mBuffer;
                        final int size = mInputStream.read(buffer);
                        writePacket(gatt, packetCharacteristic, buffer, size);
                        updateProgressNotification();
                        return;
                    } catch (final HexFileValidationException e) {
                        loge("Invalid HEX file");
                        mError = ERROR_FILE_INVALID;
                    } catch (final IOException e) {
                        loge("Error while reading the input stream", e);
                        mError = ERROR_FILE_IO_EXCEPTION;
                    }
                    break;

                case OP_CODE_RESPONSE_CODE_KEY:
                default:
                    /*
                     * If the DFU target device is in invalid state (f.e. the Init Packet is required but has not been selected), the target will send DFU_STATUS_INVALID_STATE error
                     * for each firmware packet that was send. We are interested may ignore all but the first one.
                     * After obtaining a remote DFU error the OP_CODE_RESET_KEY will be sent.
                     */
                    if (mRemoteErrorOccurred)
                        break;
                    final int status = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 2);
                    if (status != DFU_STATUS_SUCCESS)
                        mRemoteErrorOccurred = true;

                    sendLogBroadcast(LOG_LEVEL_INFO, "Notification received from " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
                    mReceivedData = characteristic.getValue();
                    break;
            }

            // Notify waiting thread
            synchronized (mLock) {
                mLock.notifyAll();
            }
        }

        // This method is repeated here and in the service class for performance matters.
        private String parse(final BluetoothGattCharacteristic characteristic) {
            final byte[] data = characteristic.getValue();
            if (data == null)
                return "";
            final int length = data.length;
            if (length == 0)
                return "";

            final char[] out = new char[length * 3 - 1];
            for (int j = 0; j < length; j++) {
                int v = data[j] & 0xFF;
                out[j * 3] = HEX_ARRAY[v >>> 4];
                out[j * 3 + 1] = HEX_ARRAY[v & 0x0F];
                if (j != length - 1)
                    out[j * 3 + 2] = '-';
            }
            return new String(out);
        }
    };



    @Override
    protected Class<? extends Activity> getNotificationTarget() {
        return NotificationActivity.class;
    }

    private static IntentFilter makeDfuActionIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(DfuBaseService.BROADCAST_ACTION);
        return intentFilter;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        final IntentFilter actionFilter = makeDfuActionIntentFilter();
        manager.registerReceiver(mDfuActionReceiver, actionFilter);
        registerReceiver(mDfuActionReceiver, actionFilter); // Additionally we must register this receiver as a non-local to get broadcasts from the notification actions

        final IntentFilter bondFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mBondStateBroadcastReceiver, bondFilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        logi("DfuService onDestroy");
        final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.unregisterReceiver(mDfuActionReceiver);

        unregisterReceiver(mDfuActionReceiver);
        unregisterReceiver(mBondStateBroadcastReceiver);
    }

    @Override
    protected BluetoothGatt connect(@NonNull final String address) {
        gatt = super.connect(address);

        mDeviceAddress = address;
        mRequestCompleted = false;
        mAborted = false;
        mPaused = false;
        mError = 0;
        mConnectionState = STATE_DISCONNECTED;
        mBytesSent = 0;
        mBytesConfirmed = 0;
        mPacketsSentSinceNotification = 0;
        mError = 0;
        mNotificationsEnabled = false;
        mResetRequestSent = false;
        mImageSizeSent = false;
        mRemoteErrorOccurred = false;

//        //For Stats purpose only
//        {
//            BluetoothGattService deviceService = gatt.getService(DEVICE_INFORMATION_SERVICE_UUID);
//            if (deviceService != null) {
//                BluetoothGattCharacteristic firmwareCharacteristic = deviceService.getCharacteristic(FIRMWARE_REVISION_UUID);
//                if (firmwareCharacteristic != null) {
//                    String firmware = null;
//                    firmware = readCharacteristicNoFailure(gatt, firmwareCharacteristic);
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
                terminateConnection(gatt, 0);
                return gatt;
            }

            final BluetoothGattCharacteristic sfpc1 = fps.getCharacteristic(MINI_FLASH_SERVICE_CONTROL_CHARACTERISTIC_UUID);
            if (sfpc1 == null) {
                logi("Error Cannot find MINI_FLASH_SERVICE_CONTROL_CHARACTERISTIC_UUID");
                terminateConnection(gatt, 0);
                return gatt;
            }

            sfpc1.setValue(1, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            try {
                logi("Writing Flash Command ....");
                writeCharacteristic(gatt, sfpc1);
                waitFor(1000);
            } catch (Exception e) {
                e.printStackTrace();
                loge(e.getMessage(), e);
            }
        }
        return gatt;
    }

    @Override
    protected boolean isDebug() {
        // Here return true if you want the service to print more logs in LogCat.
        // Library's BuildConfig in current version of Android Studio is always set to DEBUG=false, so
        // make sure you return true or your.app.BuildConfig.DEBUG here.
        return BuildConfig.DEBUG;
    }

    private void writeCharacteristic(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) throws DeviceDisconnectedException, DfuException,
            UploadAbortedException {

        gatt.writeCharacteristic(characteristic);

        // We have to wait for confirmation
        try {
            synchronized (mLock) {
                while ((!mRequestCompleted && mConnectionState == STATE_CONNECTED_AND_READY && mError == 0 && !mAborted) || mPaused) {
                    mLock.wait();
                }
            }
        } catch (final InterruptedException e) {
            loge("Sleeping interrupted", e);
        }

        if (mAborted) {
            throw new UploadAbortedException();
        }
    }

    /**
     * Writes the buffer to the characteristic. The maximum size of the buffer is 20 bytes. This method is ASYNCHRONOUS and returns immediately after adding the data to TX queue.
     *
     * @param gatt           the GATT device
     * @param characteristic the characteristic to write to. Should be the DFU PACKET
     * @param buffer         the buffer with 1-20 bytes
     * @param size           the number of bytes from the buffer to send
     */
    private void writePacket(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final byte[] buffer, final int size) {
        byte[] locBuffer = buffer;
        if (buffer.length != size) {
            locBuffer = new byte[size];
            System.arraycopy(buffer, 0, locBuffer, 0, size);
        }

        //logi("Sending Packet - " + bytesToHex(locBuffer));
        characteristic.setValue(locBuffer);
        gatt.writeCharacteristic(characteristic);
        // FIXME BLE buffer overflow
        // after writing to the device with WRITE_NO_RESPONSE property the onCharacteristicWrite callback is received immediately after writing data to a buffer.
        // The real sending is much slower than adding to the buffer. This method does not return false if writing didn't succeed.. just the callback is not invoked.
        //
        // More info: this works fine on Nexus 5 (Android 4.4) (4.3 seconds) and on Samsung S4 (Android 4.3) (20 seconds) so this is a driver issue.
        // Nexus 4 and 7 uses Qualcomm chip, Nexus 5 and Samsung uses Broadcom chips.
    }

    private void waitIfPaused() {
        synchronized (mLock) {
            try {
                while (mPaused)
                    mLock.wait();
            } catch (final InterruptedException e) {
                loge("Sleeping interrupted", e);
            }
        }
    }

    private void sendLogBroadcast(final int level, final String message) {
        final String fullMessage = "[DFU] " + message;
        final Intent broadcast = new Intent(BROADCAST_LOG);
        broadcast.putExtra(EXTRA_LOG_MESSAGE, fullMessage);
        broadcast.putExtra(EXTRA_LOG_LEVEL, level);
        broadcast.putExtra(EXTRA_DEVICE_ADDRESS, mDeviceAddress);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
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

    private void logw(final String message) {
        if (DEBUG) {
            Log.w(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    private void logi(final String message) {
        if (DEBUG) {
            Log.i(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }

    private void logd(final String message) {
        if (DEBUG) {
            Log.d(TAG, "### " + Thread.currentThread().getId() + " # " + message);
        }
    }
}
