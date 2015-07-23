package com.rajala.candroid;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ToggleButton;


import com.rajala.bluetooth.DeviceListFrag;
import com.rajala.can.CANMessage;
import com.rajala.can.ICANListener;
import com.rajala.can.device.ICANDevice;
import com.rajala.can.device.bluecan.BlueCAN;
import com.rajala.can.device.elm327.ELM327;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Set;

public class CANdroidActivity extends FragmentActivity implements DeviceListFrag.DeviceListFragListener, ICANListener {

    private static final String TAG = "CANdroid";

    // BT/CAN device
    ICANDevice canDevice;

    // Button status
    private long startTime;
    private boolean on = false;
    private int recvMode = Constants.MODE_OVERWRITE;
    private Button btn_connect;

    // RecvCAN stuff
    private CANMessage canMessage;
    private boolean newMessage = false;

    private LinkedHashMap<String, MessageInfo> myMessages = new LinkedHashMap<>();
    private ArrayList<MessageInfo> messageList = new ArrayList<>();

    private MyListAdapter listAdapter;
    private ExpandableListView myList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_candroid);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        myList = (ExpandableListView)findViewById(R.id.elv_can_messages);
        listAdapter = new MyListAdapter(getBaseContext(), messageList);
        myList.setAdapter(listAdapter);

        expandAll();
    }

    public void onStart(View v) {
        ToggleButton tbtn = (ToggleButton)v;
        on = tbtn.isChecked();
        startTime = System.currentTimeMillis();
        if (on) {
            clearAll();
        }
    }

    public void onMode(View v) {
        Button btn = (Button)v;
        if (recvMode == Constants.MODE_OVERWRITE) {
            recvMode = Constants.MODE_APPEND;
            btn.setText("Mode: Append");
        } else if (recvMode == Constants.MODE_APPEND) {
            recvMode = Constants.MODE_OVERWRITE;
            btn.setText("Mode: Overwrite");
        }
    }

    public void onConnect(View v) {
        btn_connect = (Button)v;
        DeviceListFrag deviceList = new DeviceListFrag();
        deviceList.show(getSupportFragmentManager(), "connect");
    }

    public void onDeviceListSelect(String deviceName) {
        if (canDevice != null) {
            canDevice.disconnect();
        }
        if (deviceName.contains("OBD")) {
            canDevice = new ELM327(stateHandler);
            canDevice.addCANListener(this);
        } else {
            canDevice = new BlueCAN(stateHandler);
            canDevice.addCANListener(this);
        }
    }

    public void onCANMessage(CANMessage msg) {
        Log.d(TAG, "onCANMessage");
        canMessage = msg;
        if (on) {
            final int id = canMessage.getID();
            final short[] data = canMessage.getBytes();
            switch (recvMode) {
                case Constants.MODE_OVERWRITE:
                    Log.d(TAG, "Overwriting mode:");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            long timeDiff = System.currentTimeMillis() - startTime;
                            long secs = timeDiff / 1000;
                            long millis = timeDiff % 1000;
                            String timestamp = String.format("t=%d.%03d", secs, millis);
                            String hexData = arrayToHexString(data);
                            String idStr = String.format("%03X", id);
                            overwriteData(idStr, hexData, timestamp);
                            listAdapter.notifyDataSetChanged();
                        }
                    });
                    break;
                case Constants.MODE_APPEND:
                    Log.d(TAG, "Appending mode:");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            long timeDiff = System.currentTimeMillis() - startTime;
                            long secs = timeDiff / 1000;
                            long millis = timeDiff % 1000;
                            String timestamp = String.format("t=%d.%03d", secs, millis);
                            String hexData = arrayToHexString(data);
                            String idStr = String.format("%03X", id);
                            appendData(timestamp + " " + idStr + " " + hexData, "", "");
                            listAdapter.notifyDataSetChanged();
                        }
                    });
                    break;
            }
        }
    }

    private String arrayToHexString(short[] array) {
        StringBuilder hex = new StringBuilder();
        hex.append("[");
        for (int i = 0; i < array.length; i++) {
            hex.append(String.format("%02X,", (array[i] & 0xFF)));
        }
        hex.deleteCharAt(hex.length() - 1);
        hex.append("]");
        return hex.toString();
    }

    private void expandAll() {
        int count = listAdapter.getGroupCount();
        for (int i = 0; i < count; i++){
            myList.expandGroup(i);
        }
    }

    private void clearAll() {
        Set<String> keySet = myMessages.keySet();
        for (String message : keySet) {
            clearData(message);
        }
        messageList.clear();
        listAdapter.notifyDataSetChanged();
    }

    private void clearData(String message){

        //check the hash map if the group already exists
        MessageInfo messageInfo = myMessages.get(message);
        //add the group if doesn't exists
        if (messageInfo == null){
            return;
        }

        //get the children for the group
        ArrayList<DataInfo> dataList = messageInfo.getDataList();
        dataList.clear();
        messageInfo.setDataList(dataList);
        listAdapter.notifyDataSetChanged();
    }

    private void overwriteData(String message, String data, String timestamp){

        //check the hash map if the group already exists
        MessageInfo messageInfo = myMessages.get(message);
        //add the group if doesn't exists
        if (messageInfo == null){
            messageInfo = new MessageInfo();
            messageInfo.setID(message);
            myMessages.put(message, messageInfo);
            messageList.add(messageInfo);
        }

        //get the children for the group
        ArrayList<DataInfo> dataList = messageInfo.getDataList();

        //create a new child and add that to the group
        DataInfo dataInfo = new DataInfo();
        dataInfo.setTimestamp(timestamp);
        dataInfo.setData(data);
        dataList.clear();
        dataList.add(dataInfo);
        messageInfo.setDataList(dataList);
    }

    private void appendData(String message, String data, String timestamp){

        //check the hash map if the group already exists
        MessageInfo messageInfo = myMessages.get(message);
        //add the group if doesn't exists
        if (messageInfo == null){
            messageInfo = new MessageInfo();
            messageInfo.setID(message);
            myMessages.put(message, messageInfo);
            messageList.add(messageInfo);
        }

        //get the children for the group
        ArrayList<DataInfo> dataList = messageInfo.getDataList();

        //create a new child and add that to the group
        DataInfo dataInfo = new DataInfo();
        dataInfo.setTimestamp(timestamp);
        dataInfo.setData(data);
        dataList.add(dataInfo);
        messageInfo.setDataList(dataList);
    }

    public final Handler stateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.BLUETOOTH_CONNECTED:
                    String deviceName = msg.getData().getString("deviceName");
                    btn_connect.setText(deviceName);
                    break;
                case Constants.BLUETOOTH_DISCONNECTED:
                    btn_connect.setText("Connect");
                    break;
            }
        }
    };
}
