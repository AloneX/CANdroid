package com.rajala.bluetooth;

public interface IBTListener {
    void onBTConnected(String deviceName);
    void onBTDisconnected();
}
