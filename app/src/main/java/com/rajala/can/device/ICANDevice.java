package com.rajala.can.device;

import com.rajala.can.CANMessage;
import com.rajala.can.ICANListener;

public interface ICANDevice {

    void connect(String deviceName);

    void disconnect();

    void stop();

    boolean isConnected();

    void sendCANMessage(CANMessage msg);

    void addCANListener(ICANListener listener);

    void removeCANListener(ICANListener listener);

    void clearCANListeners();
}
