package com.himelbrand.ble.peripheral;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;


import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableArray;



/**
 * {@link NativeModule} that allows JS to open the default browser
 * for an url.
 */
public class RNBLEModule extends ReactContextBaseJavaModule{

    ReactApplicationContext reactContext;
    HashMap<String, BluetoothGattService> servicesMap;
    HashSet<BluetoothDevice> mBluetoothDevices;
    BluetoothManager mBluetoothManager;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothGattServer mGattServer;
    BluetoothLeAdvertiser advertiser;
    AdvertiseCallback advertisingCallback;
    Promise writePromise;
    String name;
    boolean advertising;
    byte[] value;

    private Context context;

    public RNBLEModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.context = reactContext;
        this.servicesMap = new HashMap<String, BluetoothGattService>();
        this.advertising = false;
        this.name = "RN_BLE";
    }

    @Override
    public String getName() {
        return "BLEPeripheral";
    }

    @ReactMethod
    public void setName(String name) {
        this.name = name;
        Log.i("RNBLEModule", "name set to " + name);
    }

    @ReactMethod
    public void addService(String uuid, Boolean primary) {
        UUID SERVICE_UUID = UUID.fromString(uuid);
        int type = primary ? BluetoothGattService.SERVICE_TYPE_PRIMARY : BluetoothGattService.SERVICE_TYPE_SECONDARY;
        BluetoothGattService tempService = new BluetoothGattService(SERVICE_UUID, type);
        if(!this.servicesMap.containsKey(uuid))
            this.servicesMap.put(uuid, tempService);
    }

    @ReactMethod
    public void addCharacteristicToService(String serviceUUID, String uuid, Integer permissions, Integer properties, ReadableArray message) {
        this.value = new byte[message.size()];
        for (int i = 0; i < message.size(); i++) {
            this.value[i] = new Integer(message.getInt(i)).byteValue();
        }

        UUID CHAR_UUID = UUID.fromString(uuid);
        BluetoothGattCharacteristic tempChar = new BluetoothGattCharacteristic(CHAR_UUID, properties, permissions);
        this.servicesMap.get(serviceUUID).addCharacteristic(tempChar);
    }

    private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, final int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    mBluetoothDevices.add(device);
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    mBluetoothDevices.remove(device);
                }
            } else {
                mBluetoothDevices.remove(device);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            
            Log.i("RNBLEModule", "got read req: " + device.toString() + " " + requestId + " " + offset + " " + characteristic.toString());
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            if (offset != 0) {
                byte[] values2 = new byte[RNBLEModule.this.value.length];
                for (int i = offset; i < RNBLEModule.this.value.length; i++) {
                    values2[i - offset] = RNBLEModule.this.value[i];
                }
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, values2);
                return;
            }
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                    offset, RNBLEModule.this.value);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite,
                    responseNeeded, offset, value);
            // characteristic.setValue(value);

            Log.i("RNBLEModule", "wreq");
            WritableMap map = Arguments.createMap();
            WritableArray data = Arguments.createArray();
            for (byte b : value) {
                data.pushInt((int) b);
            }
            map.putArray("data", data);
            map.putString("device", device.toString());
            Log.i("RNBLEModule", RNBLEModule.this.writePromise.toString());
            // RNBLEModule.this.writePromise.resolve("done!");
            // RNBLEModule.this.writePromise = null;
            // // return;
            if (RNBLEModule.this.writePromise != null) {
                RNBLEModule.this.writePromise.resolve(map);
                RNBLEModule.this.writePromise = null;
            }

            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
        }
    };

    @ReactMethod
    public void start(final Promise promise){
        mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothAdapter.setName(this.name);
        // Ensures Bluetooth is available on the device and it is enabled. If not,
// displays a dialog requesting user permission to enable Bluetooth.

        mBluetoothDevices = new HashSet<>();
        mGattServer = mBluetoothManager.openGattServer(reactContext, mGattServerCallback);
        for (BluetoothGattService service : this.servicesMap.values()) {
            mGattServer.addService(service);
        }
        advertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();


        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder()
                .setIncludeDeviceName(true);
        for (BluetoothGattService service : this.servicesMap.values()) {
            dataBuilder.addServiceUuid(new ParcelUuid(service.getUuid()));
        }
        AdvertiseData data = dataBuilder.build();
        // Log.i("RNBLEModule", data.toString());

        advertisingCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                advertising = true;
                Log.e("RNBLEModule", "RESOLVING A PROMISEEE ");
                promise.resolve("Success, Started Advertising");

            }

            @Override
            public void onStartFailure(int errorCode) {
                advertising = false;
                Log.e("RNBLEModule", "Advertising onStartFailure: " + errorCode);
                promise.reject("Advertising onStartFailure: " + errorCode);
                super.onStartFailure(errorCode);
            }
        };

        advertiser.startAdvertising(settings, data, advertisingCallback);

    }
    @ReactMethod
    public void stop(){
        if (this.writePromise != null) {
            this.writePromise.reject("never wrote anything");
            this.writePromise = null;
        }
        if (mGattServer != null) {
            mGattServer.close();
        }
        if (mBluetoothAdapter !=null && mBluetoothAdapter.isEnabled() && advertiser != null) {
            // If stopAdvertising() gets called before close() a null
            // pointer exception is raised.
            advertiser.stopAdvertising(advertisingCallback);
        }
    }

    @ReactMethod
    public void getWrite(final Promise promise){
        // Ensures Bluetooth is available on the device and it is enabled. If not,
// displays a dialog requesting user permission to enable Bluetooth.
        this.writePromise = promise;
        Log.i("RNBLEModule", "getwrote");
        // this.writePromise.resolve("Done early, pls");
    }

    @ReactMethod
    public void sendNotificationToDevices(String serviceUUID,String charUUID,ReadableArray message) {
        byte[] decoded = new byte[message.size()];
        for (int i = 0; i < message.size(); i++) {
            decoded[i] = new Integer(message.getInt(i)).byteValue();
        }
        BluetoothGattCharacteristic characteristic = servicesMap.get(serviceUUID).getCharacteristic(UUID.fromString(charUUID));
        characteristic.setValue(decoded);
        boolean indicate = (characteristic.getProperties()
                & BluetoothGattCharacteristic.PROPERTY_INDICATE)
                == BluetoothGattCharacteristic.PROPERTY_INDICATE;
        for (BluetoothDevice device : mBluetoothDevices) {
            // true for indication (acknowledge) and false for notification (un-acknowledge).
            mGattServer.notifyCharacteristicChanged(device, characteristic, indicate);
        }
    }
    @ReactMethod
    public void isAdvertising(Promise promise){
        promise.resolve(this.advertising);
    }

}
