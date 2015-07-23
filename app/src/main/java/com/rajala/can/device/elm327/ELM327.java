package com.rajala.can.device.elm327;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;

import com.rajala.bluetooth.BluetoothInterface;
import com.rajala.bluetooth.IBTListener;
import com.rajala.can.CANMessage;
import com.rajala.can.ICANListener;
import com.rajala.can.device.ICANDevice;
import com.rajala.candroid.Constants;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ELM327 implements ICANDevice, IBTListener {

    private static final String TAG = "ELM327";

    public enum ELM327State {
        DISCONNECTED,
        CONNECTED,
    }
    private ELM327State currState = ELM327State.DISCONNECTED;

    // Bluetooth connection members
    private BluetoothInterface btInterface;
    private BufferedReader input;
    private OutputStream output;
    private Handler deviceHandler;

    // Incoming data handler
    private RXMonitor rxMonitor;

    // CAN message listeners
    private static List<ICANListener> listeners = Collections.synchronizedList(new ArrayList<ICANListener>());

    // === User methods ===

    public ELM327(@Nullable Handler deviceHandler) {

        // Set the state
        currState = ELM327State.DISCONNECTED;
        this.deviceHandler = deviceHandler;

        // Initialize the Bluetooth connection
        connect("OBDII");
    }

    public void connect(String deviceName) {
        btInterface = new BluetoothInterface(deviceName);
        btInterface.addBTListener(this);
    }

    public void disconnect() {
        btInterface.close();
        stop();
    }

    public boolean isConnected() {
        return (btInterface.isConnected() && (input != null) && (output != null));
    }

    public void stop() {
        if (rxMonitor != null) {
            rxMonitor.abort();
        }
    }

    public void sendOBDCommand(String cmd) {

        if (!btInterface.isConnected() || input == null || output == null) {
            Log.e(TAG, "Not connected");
            onBTDisconnected();
            return;
        }

        try {
            Log.d(TAG, "Sending: " + cmd);
            output.write((cmd + "\r").getBytes());
            output.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write to output");
            onBTDisconnected();
        }
    }

    public void sendCANMessage(CANMessage msg) {

        String hdr = String.format(ELM327Commands.AT_SET_HEADER, String.format("%03X", msg.getID()));
        String data = String.format("%016X", msg.getLongData()).substring(0, 2*msg.getDLC()) + "0";
        sendOBDCommand(hdr);
        sendOBDCommand(data);
    }

    public void addCANListener(ICANListener listener) {
        listeners.add(listener);
    }

    public void removeCANListener(ICANListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public void clearCANListeners() {
        listeners.clear();
    }

    public ELM327State getCurrState() {
        return currState;
    }

    // === Handler methods ===

    public void onBTConnected(String deviceName) {

        Log.i(TAG, "Bluetooth connected");
        try {
            input = new BufferedReader(new InputStreamReader(btInterface.getSocket().getInputStream()));
            output = btInterface.getSocket().getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "Failed to get input and output from socket");
            onBTDisconnected();
            return;
        }
        configure();
        rxMonitor = new RXMonitor();
        Thread rxThread = new Thread(rxMonitor);
        rxThread.start();
        currState = ELM327State.CONNECTED;

        // Forward the device name to the main activity handler
        if (deviceHandler == null) {
            return;
        }
        Message newDeviceMsg = deviceHandler.obtainMessage(Constants.BLUETOOTH_CONNECTED);
        Bundle newDeviceBndl = new Bundle();
        newDeviceBndl.putString("deviceName", deviceName);
        newDeviceMsg.setData(newDeviceBndl);
        deviceHandler.sendMessage(newDeviceMsg);
    }

    public void onBTDisconnected() {

        Log.i(TAG, "Bluetooth disconnected");
        input = null;
        output = null;
        currState = ELM327State.DISCONNECTED;
        if (rxMonitor != null) {
            rxMonitor.abort();
        }

        // Forward device name "none" to main activity handler
        if (deviceHandler == null) {
            return;
        }
        Message noDeviceMsg = deviceHandler.obtainMessage(Constants.BLUETOOTH_DISCONNECTED);
        Bundle noDeviceBndl = new Bundle();
        noDeviceBndl.putString("deviceName", "none");
        noDeviceMsg.setData(noDeviceBndl);
        deviceHandler.sendMessage(noDeviceMsg);
    }

    // === Internal methods ===

    private void configure() {

        sendOBDCommand(ELM327Commands.AT_RESET_ALL);
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        sendOBDCommand(String.format(ELM327Commands.AT_ECHO, 0));
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        sendOBDCommand(String.format(ELM327Commands.AT_HEADERS, 1));
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        sendOBDCommand(String.format(ELM327Commands.AT_MONITOR_ALL, 1));
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // === Receiving thread ===

    private class RXMonitor implements Runnable {

        private static final String TAG = "ELM327.RXMonitor";

        private boolean isRunning = true;

        public void run() {

            while (isRunning) {
                if (currState == ELM327State.CONNECTED) {
                    try {
                        String newLine = input.readLine();
//                        Log.d(TAG, "newLine = " + newLine);
                        if (newLine.contains("BUFFER FULL")) {
                            sendOBDCommand(String.format(ELM327Commands.AT_MONITOR_ALL, 1));
                        }
                        CANMessage msg = parseCANMessage(newLine);
                        if (msg == null) {
                            continue;
                        }
                        synchronized (listeners) {
                            Log.d(TAG, "Calling all listeners");
                            for (ICANListener listener : listeners) {
                                Log.d(TAG, "Found a listener");
                                listener.onCANMessage(msg);
                            }
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Buffered reader failed");
                        onBTDisconnected();
                    }
                } else {
                    Thread.yield();
                }
            }
        }

        public void abort() {
            isRunning = false;
        }

        private CANMessage parseCANMessage(String line) {

            CANMessage msg = new CANMessage();

            if (line.length() == 0) {
                Log.d(TAG, "CAN Line = " + line);
                return null;
            }

            String[] contents = line.split(" ");

            // Parse 3 digit ID
            String idString;
            if (contents[0].charAt(0) == '>') {
                idString = contents[0].substring(1, contents[0].length());
            } else {
                idString = contents[0].substring(0, contents[0].length());
            }
            int id;
            try {
                id = Integer.parseInt(idString, 16);
            } catch (NumberFormatException e) {
                Log.d(TAG, "CAN Line = " + line);
                return null;
            }


            // Parse data bytes until no more numbers
            int dataLength = 8;
            if (contents.length < 9) {
                dataLength = contents.length - 1;
            }
            short[] data = new short[dataLength];
            for (int i = 0; i < dataLength; i++) {
                try {
                    data[i] = Short.parseShort(contents[i + 1], 16);
                } catch (NumberFormatException e) {
                    break;
                }
            }

            Log.d(TAG, "CAN message = " + id + ":" + Arrays.toString(data));

            // Set CAN message
            msg.setID(id);
            msg.setBytes(data);

            return msg;
        }
    }
}
