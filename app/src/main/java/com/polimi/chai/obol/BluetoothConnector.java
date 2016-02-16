package com.polimi.chai.obol;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


/**
 * Created by Chai on 29/09/2015.
 */
public class BluetoothConnector {

    public static final int MESSAGE_STRING = 2;
    public static final int MESSAGE_BLUETOOTH = 3;
    /*Debug flag*/
    private final static boolean D = true;
    private final String TAG = BluetoothConnector.this.getClass().getName();
    // Well known SPP UUID
    private final UUID UUID_SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter mBluetoothAdapter;
    private Context context;
    private Handler mHandler;

    private BroadcastReceiver mReceiver;
    private Set<BluetoothDevice> mDevices;
    private TextView tv;


    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    // constructor

    /**
     * @param context to register the BroadcastReceiver
     * @param tv      to show some output on UIThread
     * @param handler to exchange messages between threads
     */

    public BluetoothConnector(Context context, final TextView tv, Handler handler) {

        this.context = context;
        this.tv = tv;
        mHandler = handler;

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mDevices = new HashSet<>();

        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                // When discovery finds a device
                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    // Get the BluetoothDevice object from the Intent
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    // Add the name and address to an array adapter to show in a ListView

                    if (!mDevices.contains(device)) {
                        mDevices.add(device);
                        mHandler.obtainMessage(BluetoothConnector.MESSAGE_BLUETOOTH, device).sendToTarget();
                    }
                    String devices = "";
                    for (BluetoothDevice b : mDevices) {
                        devices = devices + b.getName() + "\n";
                    }
                    Log.d("DEVICES FOUND", devices);
                    tv.setText(devices);
                }
            }
        };
    }

    /**
     * @return HashSet<BluetoothDevice>, only paired devices
     */
    public Set<BluetoothDevice> getPairedDevices() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        // If there are paired devices
        if (pairedDevices.size() > 0) {
            return pairedDevices;
        } else return null;
    }

    /**
     * @return boolean saying if bluetooth is enabled. It return false if it is not supported or disabled
     */
    public boolean isEnable() {
        if (isSupported()) {
            if (!mBluetoothAdapter.isEnabled()) {
                return false;
            } else {
                return true;
            }
        }
        return false;
    }

    /**
     * @return boolean it says if bluetooth is supported
     */
    protected boolean isSupported() {
        if (mBluetoothAdapter == null) {
            return false;
        } else return true;
    }

    /**
     * register the BroadcastReceiver for bluetooth discovery
     */
    public void registerForDevices() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.context.registerReceiver(mReceiver, filter);

    }

    /**
     * unregister the BroadcastReceiver for bluetooth discovery
     */
    public void unRegisterForDevices() {

        this.context.unregisterReceiver(mReceiver);

    }

    /**
     * @return BluetoothAdapter the adapter
     */
    public BluetoothAdapter getAdapter() {
        return this.mBluetoothAdapter;
    }

    /**
     * @return the BroadcastReceiver used to retrieve the devices discovered
     */
    public BroadcastReceiver getBroadcastReceiver() {

        return this.mReceiver;
    }

    /**
     * @return the set of all devices
     */
    public Set<BluetoothDevice> getDevices() {
        return this.mDevices;
    }

    /**
     * @param device the bluettotj device we want to connect to
     *               It start the connection launching a ConnectThread
     */
    public synchronized void connect(BluetoothDevice device) {
        if (D) Log.d(TAG, "connect to: " + device.getName());

        // Cancel any thread attempting to make a connection

        if (mConnectThread != null) {
            try {
                mConnectThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }


        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            try {
                mConnectedThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Start the thread to connect with the given device
        mConnectThread = new ConnectThread(device);
        // mBluetoothAdapter.cancelDiscovery();
        mConnectThread.start();

    }

    /**
     * @param bDevice the device we are connecting to
     * @param bSocket the socket used to send-receivee data from-to the bDevice
     */
    public synchronized void manageConnectedSocket(BluetoothSocket bSocket, BluetoothDevice bDevice) {

        if (D) Log.d(TAG, "manageConnectedSocket called with socket: " + bSocket.toString());
        // Cancel the thread that completed the connection
        if (mConnectThread != null) {

            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {

            mConnectedThread.cancel();
            mConnectedThread = null;

        }

        mConnectedThread = new ConnectedThread(bSocket);
        if (D) Log.d(TAG, "Connected Thread started");
        mConnectedThread.start();

    }

    public void sendData(String data) {
        if (mConnectedThread != null) {
            mConnectedThread.write(data);
        }

    }

    /**
     * Thread used to connect to the device
     */
    private class ConnectThread extends Thread {

        /**
         * the Bluetoothdevice
         */
        private final BluetoothDevice mDevice;
        /**
         * the BluetoothSocket
         */
        private BluetoothSocket mSocket;

        /**
         * @param device the BluetoothDevice we want to connect to
         */
        public ConnectThread(BluetoothDevice device) {
            this.mDevice = device;
            BluetoothSocket tempSocket = null;

            try {
                //establish an insecure connection because the devices has not security check system
                ParcelUuid list[] = this.mDevice.getUuids();
                // UUID u = list[0].getUuid();
                if (list != null) {
                    if (list.length != 0)
                        tempSocket = this.mDevice.createInsecureRfcommSocketToServiceRecord(list[0].getUuid());
                } else
                    tempSocket = this.mDevice.createInsecureRfcommSocketToServiceRecord(UUID_SPP);

            } catch (IOException e) {
                e.printStackTrace();
            }
            mSocket = tempSocket;

        }

        public void run() {
            //remeber to cancel discovery before calling the thread
            try {
                if (D) Log.i(TAG, "connecting ...");
                mSocket.connect();
                if (D) Log.i(TAG, "connected");
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    if (D) Log.i(TAG, "trying fallback...");
                    mSocket = (BluetoothSocket) mDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(mDevice, 1);
                    mSocket.connect();

                    if (D) Log.i(TAG, "Connected");
                } catch (Exception e2) {
                    Log.e(TAG, "Couldn't establish Bluetooth connection!");

                    if (D) Log.i(TAG, "Connection failed");
                    return;
                }

            }

            manageConnectedSocket(mSocket, mDevice); //manage the conenction and input-output stream from the socket
        }

        /**
         * Will cancel an in-progress connection, and close the socket
         */
        public void cancel() {

        }

    }

    /**
     * ConnectedThread is used to manage the data exchanges between the two devices once thery get already connected
     */
    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final OutputStream mmOutStream;

        private final InputStream mImpIn;

        public ConnectedThread(BluetoothSocket socket) {

            mmSocket = socket;
            OutputStream tmpOut = null;
            InputStream tmpIn = null;
            // Get the BluetoothSocket input and output streams from socket here
            try {
                tmpIn = socket.getInputStream();

                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
                if (D)
                    Log.e(TAG, "temp sockets not created", e);
            }
            mmOutStream = tmpOut;
            mImpIn = tmpIn;
        }

        /**
         * We are reading only string data from the InputStream
         */
        public void run() {
            if (D)
                Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes = -1;


            // Keep listening to the InputStream while connected
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mImpIn.read(buffer);
                    if (bytes != -1) {
                        String data = new String(buffer, 0, bytes);
                        if (D)
                            Log.i(TAG, "sending data");
                        mHandler.obtainMessage(BluetoothConnector.MESSAGE_STRING, data).sendToTarget();
                    }

                } catch (IOException e) {
                    if (D)
                        Log.e(TAG, "disconnected", e);
                    e.printStackTrace();

                    break;
                }
            }
        }

        public void write(String data) {
            byte[] buffer = data.getBytes();
            try {
                mmOutStream.write(buffer);

                Log.d(TAG, "sending data: " + data);
                mHandler.obtainMessage(BluetoothConnector.MESSAGE_STRING, data).sendToTarget();
                //send what is already in buffer
                //mmOutStream.flush();


            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }


        /**
         * method to cancel the ConnectedThread once we are done with the data exchanges
         */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
                if (D)
                    Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

}

