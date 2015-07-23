package com.rajala.can;

public interface ICANListener {
    // Recommended that onCANMessage starts a new thread with a Runnable implementation to handle
    // the message for best performance
    void onCANMessage(CANMessage msg);
}
