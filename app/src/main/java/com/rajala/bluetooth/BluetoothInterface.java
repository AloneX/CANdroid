package com.rajala.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothInterface {

    private static final String TAG = "BluetoothInterface";

    // Connection members
    private static final String defaultUUID = "00001101-0000-1000-8000-00805F9B34FB";
    private BluetoothAdapter btAdapter;
    private IBTListener listener;

    // I/O interfaces
    private BluetoothSocket btSocket;
    private InputStream btInputStream;
    private InputStreamReader btInputStreamReader;
    private BufferedReader btBufferedReader;
    private OutputStream btOutputStream;

    // Configuration parameters
    private int retryCount = 0;
    private int retryThreshold = 50;

    public BluetoothInterface(String deviceName) {

        retryCount = 0;

        // Init BT
        if (!init()) {
            return;
        }

        // Search for the device by name
        for (BluetoothDevice device : btAdapter.getBondedDevices()) {
            Log.i(TAG, device.getName());
            if (device.getName().equals(deviceName)) {
                ConnectThread connectThread = new ConnectThread(device);
                connectThread.start();
                return;
            }
        }
    }

    private void restart(BluetoothDevice device) {

        // Check retry count
        retryCount++;
        Log.d(TAG, "restarting the Bluetooth interface, retryCount = " + retryCount);
        if (retryCount > retryThreshold) {
            Log.d(TAG, "FAILURE: could not connect in " + retryThreshold + " attempts");
            return;
        }

        // Init BT
        if (!init()) {
            return;
        }

        // Connect to the device
        ConnectThread connectThread = new ConnectThread(device);
        connectThread.start();
    }

    private boolean init() {

        // Get the adapter and make sure it's on
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            return false;
        }
        if (!btAdapter.isEnabled()) {
            btAdapter.enable();
        }
        if (!btAdapter.isEnabled()) {
            Log.d(TAG, "FAILURE: User did not enable Bluetooth");
            return false;
        }
        return true;
    }

    public void close() {

        // Try to close everything
        Log.d(TAG, "Close()");
        try {
            btSocket.close();
        } catch (Exception e) {
            Log.d(TAG, "W: failed to close BluetoothSocket during close()");
        }
        btSocket = null;

        // Send the disconnected message and pause briefly for system
        listener.onBTDisconnected();
    }

    public boolean isConnected() {

        boolean connected;
        try {
            connected = btSocket.isConnected();
        } catch (NullPointerException e) {
            connected = false;
        }
        return connected;
    }

    public void addBTListener(IBTListener listener) {
        this.listener = listener;
    }

    // === I/O interface methods ===

    public BluetoothSocket getSocket() {
        return btSocket;
    }

    // === Separate thread to connect to devices ===

    private class ConnectThread extends Thread {

        private BluetoothDevice btDevice;
        private boolean startup = true;

        public ConnectThread(BluetoothDevice device) {

            btDevice = device;
            try {
                btSocket = btDevice.createRfcommSocketToServiceRecord(UUID.fromString(defaultUUID));
            } catch (IOException e) {
                Log.d(TAG, "E: failed to create socket");
                btSocket = null;
                retryCount++;
                restart(btDevice);
            }
        }

        public void run() {

            // Cancel discovery because it's slow
            btAdapter.cancelDiscovery();
            Log.d(TAG, "started run");

            // Connect to the socket
            try {
                btSocket.connect();
                retryCount = 0;
                String deviceName = btDevice.getName();
                Log.d(TAG, "SUCCESS: connected to " + deviceName);
                // Send the new device name back to the fragment to update the view
                listener.onBTConnected(deviceName);
            } catch (Exception e) {
                close();
                restart(btDevice);
            }
        }
    }
}
