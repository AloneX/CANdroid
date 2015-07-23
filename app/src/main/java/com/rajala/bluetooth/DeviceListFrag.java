package com.rajala.bluetooth;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.rajala.candroid.R;

import java.util.Set;

public class DeviceListFrag extends DialogFragment {

    private static final String TAG = "DeviceList";

    private String deviceName = "";

    private BluetoothAdapter btAdapter;

    public interface DeviceListFragListener {
        void onDeviceListSelect(String deviceName);
    }
    DeviceListFragListener listener;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            listener = (DeviceListFragListener)activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement DeviceListFragListener");
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.device_list, null);
        builder.setView(view);

        // Initialize device arrays
        ArrayAdapter<String> pairedDeviceAdapter = new ArrayAdapter<String>(builder.getContext(), R.layout.device_name);

        // Set up the paired list view
        ListView pairedList = (ListView)view.findViewById(R.id.paired_devices);
        pairedList.setAdapter(pairedDeviceAdapter);
        pairedList.setOnItemClickListener(deviceClickListener);

        // Get the currently paired devices
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            view.findViewById(R.id.tv_paired_devices).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices) {
                pairedDeviceAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            String noDevices = view.getResources().getText(R.string.none_paired).toString();
            pairedDeviceAdapter.add(noDevices);
        }

        return builder.create();
    }

    private AdapterView.OnItemClickListener deviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

            // Cancel discovery for speed
            btAdapter.cancelDiscovery();

            // Get the device name
            String info = ((TextView)v).getText().toString();
            deviceName = info.substring(0, info.length() - 18);
            DeviceListFragListener activity = (DeviceListFragListener)getActivity();
            activity.onDeviceListSelect(deviceName);
            dismiss();
        }
    };
}
