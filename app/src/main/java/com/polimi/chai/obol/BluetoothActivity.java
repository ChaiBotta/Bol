package com.polimi.chai.obol;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashSet;
import java.util.Set;


/**
 * Created by Chai on 29/09/2015.
 */
public class BluetoothActivity extends Activity {

    private static final String MESSAGE_NOT_SUPPORTED = "BLUETOOTH NOT SUPPORTED";
    private static final String MESSAGE_DISCOVERING = "DISCOVERING IN PROCESS";
    private static final int REQUEST_ENABLE_BT = 2;
    private final static String SAMPLE_ARRAY = "SAMPLE_ARRAY";
    private final String TAG = BluetoothActivity.this.getClass().getName();
    private BluetoothConnector bConnector;
    private boolean bluetoothEnable = false;
    private TextView text;
    private TextView text2;
    private LinearLayout rootLayout;
    //BUFFER USED TO STORE INPUT STREAM FORM BLUETOOTH
    private StringBuffer buffer = new StringBuffer();
    /******
     * ZECG variables
     ******/

    private String fileName;
    private FileOutputStream fileOutputStream;
    private OutputStreamWriter outputStreamWriter;
    private WriteThread writeThread;
    private final Handler mHandler = new Handler() {


        public void handleMessage(Message msg) {
            if (msg.what == BluetoothConnector.MESSAGE_STRING) {
                //manage the message received from Bluetooth input stream
                buffer.append((String) msg.obj);
                storeAndConvertData();
                Log.d(TAG, (String) msg.obj);
            } else if (msg.what == BluetoothConnector.MESSAGE_BLUETOOTH) {
                //manage the message received from Bluetooth input stream
                //DISCOVERED ANOTHER BLUETOOTH
                //mArrayAdapter.add((BluetoothDevice)msg.obj);
                addButtonDevice((BluetoothDevice) msg.obj);
            }

        }
    };
    private Set<BluetoothDevice> mArrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);


        //mArrayAdapter = new HashSet<>();
        loadUIElement();
        mArrayAdapter = new HashSet<>();
        bConnector = new BluetoothConnector(getApplicationContext(), text, mHandler);


    }

    //wrap ui elements
    public void loadUIElement() {
        text = (TextView) findViewById(R.id.text);
        text2 = (TextView) findViewById(R.id.text2);
        rootLayout = (LinearLayout) findViewById(R.id.rootLayout);
    }

    public void addButtonDevice(final BluetoothDevice device) {
        Button temp = new Button(getApplicationContext());
        temp.setText(device.getName());
        temp.setWidth(LinearLayout.LayoutParams.MATCH_PARENT);
        temp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               // bConnector.getAdapter().cancelDiscovery();

                bConnector.connect(device);
            }
        });
        temp.setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
        rootLayout.addView(temp);
    }

    public void sendDataToObol(View v) {
        EditText rgbtext = (EditText) findViewById(R.id.text_rgb);
        String editValue = rgbtext.getText().toString();
        if (!editValue.isEmpty()) {
            bConnector.sendData(editValue);
        }
    }

    //discover new devices
    public void discover(View v) {

        if (bConnector.isSupported()) {
            if (!bConnector.isEnable()) {
                goEnableBluetooth();
            } else {
                //stard discovering
                text.setText(MESSAGE_DISCOVERING);
                bConnector.getAdapter().startDiscovery();
            }
        } else {
            text.setText(MESSAGE_NOT_SUPPORTED);
            //bluetooth is not supported by the device
        }
    }

    public void showPairedDevices(View v) {
        String values = "";
        if (bConnector.getPairedDevices() != null)
            for (BluetoothDevice b : bConnector.getPairedDevices()) {
                values = values + b.getName() + "\n";
            }
        text2.setText(values);

    }

    protected void goEnableBluetooth() {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        bConnector.registerForDevices();

    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        bConnector.unRegisterForDevices();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

    private Integer[] readUntil(String c) {
        String data = "";
        int index = buffer.indexOf(c, 0);
        if (index > -1) {
            data = buffer.substring(0, index + c.length());
            buffer.delete(0, index + c.length());
        }
        //remove the battery and ignore lines with less than 8 signals
        if (data.length() < 32)
            return null;

        /*****Pass to the WriteThread the Sample****/

        Message msg = Message.obtain();
        msg.obj = data;
        writeThread.mHandler.sendMessage(msg);

        /****************************/

        /*******Read conversion******/


        Integer[] signal = new Integer[8];

        int j = 0;
        for (int s = 0; s < 32; s += 4) {
            String currentString = data.substring(s, s + 3);
            signal[j] = Integer.parseInt(currentString, 16);
            j++;
        }

        /****************************/

        return signal;

    }

    //parse and store all data
    private void storeAndConvertData() {
        Integer[] data = readUntil("\n");
        Intent dataIntent = new Intent();
        dataIntent.putExtra(SAMPLE_ARRAY, data);
        LocalBroadcastManager.getInstance(this).sendBroadcast(dataIntent);
        if (data != null) {
            storeAndConvertData();
        }
    }

    /**
     * Write Thread handling writing operation to the device
     */

    private class WriteThread extends Thread {

        public Handler mHandler;

        @Override
        public void run() {
            Looper.prepare();

            mHandler = new Handler() {
                public void handleMessage(Message msg) {
                    String data = (String) msg.obj;
                    if (outputStreamWriter != null) {
                        try {
                            outputStreamWriter.append(data);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            };
            Looper.loop();
        }
    }
}
