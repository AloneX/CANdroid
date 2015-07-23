package com.rajala.can.device.bluecan;

import android.bluetooth.BluetoothSocket;
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
import java.util.Collections;
import java.util.List;

public class BlueCAN implements ICANDevice, IBTListener {

    private static final String TAG = "BlueCAN";

    // Device states
    private enum BlueCANState {
        DISCONNECTED,
        CONFIGURABLE,
        CONNECTED,
    }

    // Supported commands
    private class BlueCANCommand {
        public static final String CAN_SET_BAUD = "CAN%d:INIT:%d";
        public static final String CAN_SEND_MESSAGE = "CAN%d:%X:";
        public static final String CAN_SEND_MESSAGE_PERIODIC = "CONF:CAN%d:CYC%d:%d:%X:";
        public static final String CAN_TURN_PERIODIC_ON = "CONF:CAN%d:CYC%d:ON";
        public static final String CAN_TURN_PERIODIC_OFF = "CONF:CAN%d:CYC%d:OFF";
    }

    // Object states
    private BlueCANState blueCANState;
    public boolean isRunning;

    // Bluetooth I/O
    private String defaultName = "BlueCAN  9";
    private BluetoothInterface btInterface;
    private BufferedReader input;
    private OutputStream output;
    private Handler deviceHandler;

    // CAN network members
    private static List<ICANListener> listeners = Collections.synchronizedList(new ArrayList<ICANListener>());
    private Thread rxThread;

    // === User Methods ===

    public BlueCAN(@Nullable Handler deviceHandler) {

        // Set the state
        blueCANState = BlueCANState.DISCONNECTED;
        this.deviceHandler = deviceHandler;
        isRunning = true;

        // Initialize the Bluetooth connection
        connect(defaultName);
    }

    public void connect(String deviceName) {
        btInterface = new BluetoothInterface(deviceName);
        btInterface.addBTListener(this);
    }

    public void disconnect() {
        btInterface.close();
    }

    public boolean isConnected() {
        return (btInterface.isConnected() && (input != null) && (output != null) && (blueCANState == BlueCANState.CONNECTED));
    }

    public void stop() {
        isRunning = false;
        disconnect();
    }

    public void sendCANMessage(CANMessage msg) {
        sendCommand(buildCANCommand(msg));
    }

//    public void sendCANMessage(CANMessage msg) {
//        txMessages.add(msg);
//    }

    public void addCANListener(ICANListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    public void removeCANListener(ICANListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    public void clearCANListeners() {
        listeners.clear();
    }

    // === Internal Methods ===

    private void configure() {
        sendCommand(String.format(BlueCANCommand.CAN_SET_BAUD, 1, 500000 / 1000));
    }

    private boolean getIODevices() {

        // Get the devices from the Bluetooth Interface
        Log.d(TAG, "I: getting new I/O devices");
        BluetoothSocket btSocket = btInterface.getSocket();
        try {
            input = new BufferedReader(new InputStreamReader(btSocket.getInputStream()));
            output = btSocket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "Could not get IO devices from btSocket");
            return false;
        }

        return true;
    }

    // === RX and TX methods ===

    private class BlueCANRXThread implements Runnable {

        private boolean recvToggle = true;
        private CANMessage lastMsg = new CANMessage();

        public void run() {

            while (isRunning) {
                // If disconnected, do nothing until reconnected
                if (blueCANState == BlueCANState.DISCONNECTED) {
                    continue;
                }
                // Read the data
                String data = null;
                try {
                    data = input.readLine();
                    Log.d(TAG, "Message received " + data);
                    if (data == null) {
                        continue;
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Caught exception reading line in BlueCAN");
                }
                // Process the data
                String[] tokens = data.trim().split(":");
                if (tokens.length == 3) {
                    CANMessage msg = parseCANMessage(tokens);
                    if (msg == null) {
                        continue;
                    }
                    // Dispatch the messages
                    if (recvToggle || differentReply(msg, lastMsg)) {
                        synchronized (listeners) {
                            for (ICANListener listener : listeners) {
                                listener.onCANMessage(msg);
                            }
                        }
                        recvToggle = false;
                    } else {
                        recvToggle = true;
                    }
                    lastMsg = msg;
                } else {
                    Log.i(TAG, "Unprocessed message = " + data);
                }
            }

            Log.d(TAG, "Ending BlueCANRXThread run");
        }

        private CANMessage parseCANMessage(String[] tokens) {
            try {

                CANMessage msg = new CANMessage();
                msg.setID(Integer.parseInt(tokens[1], 16));
                int dlc = tokens[2].length() / 2;
                if ((dlc < 0) || (dlc > 8)) {
                    Log.e(TAG, "Invalid DLC error");
                    return null;
                }
                byte[] data = new byte[dlc];
                for (int ib = 0; ib < dlc; ib++) {
                    Log.d(TAG, "b = " + tokens[2].substring((ib*2), (ib*2)+2));
                    data[ib] = (byte)Integer.parseInt(tokens[2].substring((ib*2), (ib*2)+2), 16);
                }
                msg.setBytes(data);
                return msg;
            } catch (Exception e) {
                Log.e(TAG, "Error while parsing CAN message");
                return null;
            }
        }

        private boolean differentReply(CANMessage msg1, CANMessage msg2) {
            short[] b1 = msg1.getBytes();
            short[] b2 = msg2.getBytes();
            if (b1.length != b2.length) {
                return true;
            }
            for (int i = 0; i < b1.length; i++) {
                if (b1[i] != b2[i]) {
                    return true;
                }
            }
            return false;
        }
    }

    private void sendCommand(String cmd) {

        // Check I/O devices
        if (input == null || output ==null) {
            Log.e(TAG, "No IO devices in sendCommand");
            return;
        }

        // Write to output device
        Log.d(TAG, "Sending: " + cmd);
        try {
            output.write((cmd + "\r").getBytes());
            output.flush();
        } catch (Exception e) {
            Log.e(TAG, "Caught exception during sendCommand");
            disconnect();
        }
    }

    private String buildCANCommand(CANMessage msg) {
        String cmd = String.format(BlueCANCommand.CAN_SEND_MESSAGE, msg.getChannel(), msg.getID());
        short[] msgData = msg.getBytes();
        for (int ib = 0; ib < msg.getDLC(); ib++) {
            cmd += String.format("%02X", msgData[ib]);
        }
        return cmd;
    }

    // === Bluetooth Handler ===

    public void onBTConnected(String deviceName) {

        // Get new IO devices
        if (!getIODevices()) {
            Log.e(TAG, "Could not get IO devices, disconnecting from handler");
            disconnect();
            return;
        }

        // Configure the device and start monitoring
        configure();
        rxThread = new Thread(new BlueCANRXThread());
        rxThread.start();
        blueCANState = BlueCANState.CONNECTED;

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

        // Clear interface and set state to disconnected
        input = null;
        output = null;
        blueCANState = BlueCANState.DISCONNECTED;

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
}
