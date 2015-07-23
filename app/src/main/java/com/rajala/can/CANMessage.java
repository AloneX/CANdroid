package com.rajala.can;

import android.support.annotation.Nullable;

public class CANMessage {

    private static final String TAG = "CANMessage";

    private int channel;
    private int id;
    private short[] data;
    private boolean periodic;
    private int period;

    public CANMessage() {
        channel = 1;
        id = 0;
        data = new short[8];
        periodic = false;
        period = 50;
    }

    public CANMessage(int channel, int id, int dlc, boolean periodic, @Nullable int period) {
        this.channel = channel;
        this.id = id;
        this.periodic = periodic;
        this.period = period;
        data = new short[dlc];
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }

    public void setID(int id) {
        this.id = id;
    }

    public void setBytes(byte[] data) {
        this.data = new short[data.length];
        for (int i = 0; i < data.length; i++) {
            this.data[i] = (short)data[i];
        }
    }

    public void setBytes(short[] data) {
        this.data = data;
    }

    public void setPeriodic(boolean periodic) {
        this.periodic = periodic;
    }

    public void setPeriod(int period) {
        this.period = period;
    }

    public int getChannel() {
        return channel;
    }

    public int getID() {
        return id;
    }

    public int getDLC() {
        return data.length;
    }

    public short[] getBytes() {
        return data;
    }

    public long getLongData() {
        long longData = 0;
        for (int i = 0; i < data.length; i++) {
            longData |= ((long)(data[i] & 0xFF) << (i*8));
        }
        longData = Long.reverseBytes(longData);
        return longData;
    }

    public boolean isPeriodic() {
        return periodic;
    }

    public int getPeriod() {
        return period;
    }
}
