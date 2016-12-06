/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ble.example.ble_component;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;


/**
* Service for managing connection and data communication with a GATT server hosted on a
* given Bluetooth LE device.
*/
public class BluetoothLeService extends Service {
   private final static String TAG = BluetoothLeService.class.getSimpleName();

   private BluetoothManager mBluetoothManager;
   private BluetoothAdapter mBluetoothAdapter;
   private HashMap<String, BluetoothGatt> mBluetoothGattHashMap = new HashMap<>();

   public final static String ACTION_DEVICE_PAIRED = "";
   public final static String ACTION_DEVICE_UNPAIR = "";
   public final static String ACTION_GATT_CONNECTED = "";
   public final static String ACTION_GATT_DISCONNECTED = "";
   public final static String ACTION_GATT_SERVICES_DISCOVERED = "";
   public final static String ACTION_DATA_AVAILABLE = "";
   public final static String ACTION_DATA_READ = "";
   public final static String ACTION_DATA_WRITE = "";
   public final static String ACTION_DESCRIPTOR_WRITE = "";

   public final static String EXTRA_DATA = "";
   public final static String DEVICE_ADDRESS = "";
   public final static String DEVICE_NAME = "";
   public final static String DATA_UUID = "";

   private boolean mIsCreateBond = false;

   // Implements callback methods for GATT events that the app cares about.  For example,
   // connection change and services discovered.
   private BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
       @Override
       public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
           String intentAction;
           if (newState == BluetoothProfile.STATE_CONNECTED) {
               if (status == 0) {
                   intentAction = ACTION_GATT_CONNECTED;
                   broadcastUpdate(intentAction, gatt.getDevice().getAddress(), gatt.getDevice().getName());
                   // Attempts to discover services after successful connection.
                   mBluetoothGattHashMap.put(gatt.getDevice().getAddress(), gatt);
               } else {
                   if (gatt != null) {
                       gatt.disconnect();
                       gatt.close();
                   }
               }
           } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
               intentAction = ACTION_GATT_DISCONNECTED;
               broadcastUpdate(intentAction, gatt.getDevice().getAddress(), gatt.getDevice().getName());
               gatt.close();
           }
       }

       @Override
       public void onServicesDiscovered(BluetoothGatt gatt, int status) {
           if (status == BluetoothGatt.GATT_SUCCESS) {
               broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED, gatt.getDevice().getAddress(), gatt.getDevice().getName());
           }
       }

       @Override
       public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
           if (status == BluetoothGatt.GATT_SUCCESS) {
               broadcastUpdate(ACTION_DATA_READ, characteristic, gatt.getDevice().getAddress(), gatt.getDevice().getName());
           }
       }

       @Override
       public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
           broadcastUpdate(ACTION_DATA_WRITE, characteristic, gatt.getDevice().getAddress(), gatt.getDevice().getName());

       }

       @Override
       public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
           if (status == BluetoothGatt.GATT_SUCCESS) {
               broadcastUpdate(ACTION_DESCRIPTOR_WRITE, gatt.getDevice().getAddress(), gatt.getDevice().getName());
           }
       }

       @Override
       public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
           broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic, gatt.getDevice().getAddress(), gatt.getDevice().getName());
       }
   };

   private void broadcastUpdate(final String action, String address, String deviceName) {
       final Intent intent = new Intent(action);
       intent.putExtra(DEVICE_ADDRESS, address);
       intent.putExtra(DEVICE_NAME, deviceName);
       sendBroadcast(intent);
   }

   private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic, String
           address, String deviceName) {
       final Intent intent = new Intent(action);
       intent.putExtra(DEVICE_ADDRESS, address);
       intent.putExtra(DEVICE_NAME, deviceName);

       final byte[] data = characteristic.getValue();
       if (data != null && data.length > 0) {
           intent.putExtra(EXTRA_DATA, data);
       }
       intent.putExtra(DATA_UUID, characteristic.getUuid().toString());
       sendBroadcast(intent);
   }

   public class LocalBinder extends Binder {
       public BluetoothLeService getService() {
           return BluetoothLeService.this;
       }
   }

   @Override
   public IBinder onBind(Intent intent) {
       return mBinder;
   }

   @Override
   public boolean onUnbind(Intent intent) {
       // After using a given device, you should make sure that BluetoothGatt.close() is called
       // such that resources are cleaned up properly.  In this particular example, close() is
       // invoked when the UI is disconnected from the Service.
//        close();
       return super.onUnbind(intent);
   }

   private final IBinder mBinder = new LocalBinder();

   /**
    * Initializes a reference to the local Bluetooth adapter.
    *
    * @return Return true if the initialization is successful.
    */
   public boolean initialize() {
       // For API level 18 and above, get a reference to onClientRegistered through BluetoothManager.
       if (mBluetoothManager == null) {
           mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
           if (mBluetoothManager == null) {
               return false;
           }
       }

       mBluetoothAdapter = mBluetoothManager.getAdapter();
       if (mBluetoothAdapter == null) {
           return false;
       }

       IntentFilter intent = new IntentFilter();
       intent.addAction(BluetoothDevice.ACTION_FOUND);
       intent.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
       intent.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);
       intent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
       registerReceiver(mBleBroadcastReceiver, intent);

       return true;
   }

    /**
     * unregister broadcast
     */
    public void unRegisterBleBroadcastReceiver(){
        if (mBleBroadcastReceiver != null){
            unregisterReceiver(mBleBroadcastReceiver);
        }
    }


   private BroadcastReceiver mBleBroadcastReceiver = new BroadcastReceiver() {

       public void onReceive(Context context, Intent intent) {
           String action = intent.getAction();
           if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
               BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
               switch (device.getBondState()) {
                   case BluetoothDevice.BOND_BONDING:
                       break;
                   case BluetoothDevice.BOND_BONDED:

                           broadcastUpdate(ACTION_DEVICE_PAIRED, device.getAddress(), device.getName());
                       mIsCreateBond = false;
                       break;
                   case BluetoothDevice.BOND_NONE:
                       broadcastUpdate(ACTION_DEVICE_UNPAIR, device.getAddress(), device.getName());
                       if (mIsCreateBond) {
                           Toast.makeText(BluetoothLeService.this, "busy", Toast.LENGTH_SHORT).show(); // string later
                       }
                       mIsCreateBond = false;
                       break;
                   default:
                       break;
               }
           }
       }
   };

   /**
    * Connects to the GATT server hosted on the Bluetooth LE device.
    *
    * @param address The device address of the destination device.
    * @return Return true if the connection is initiated successfully. The connection result
    * is reported asynchronously through the
    * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
    * callback.
    */
   public boolean connect(final String address) {
       if (mBluetoothAdapter == null || address == null) {
           return false;
       }

       BluetoothGatt gatt = mBluetoothGattHashMap.get(address);

       final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
       if (device == null) {
           return false;
       }

       // We want to directly connect to the device, so we are setting the autoConnect
       // parameter to false.
       gatt = device.connectGatt(this, false, mBluetoothGattCallback);
       return true;

   }

    /**
     * pairing process
     * @param device
     */
   public void bondDevice(BluetoothDevice device) {
       if (device == null) {
           return;
       }
       device.createBond();
       mIsCreateBond = true;
   }

   /**
    * Disconnects an existing connection or cancel a pending connection. The disconnection result
    * is reported asynchronously through the
    * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
    * callback.
    */
   public void disconnect(String address) {
       if (mBluetoothAdapter == null || mBluetoothGattHashMap.get(address) == null) {
           return;
       }

       mBluetoothGattHashMap.get(address).disconnect();
       close(mBluetoothGattHashMap.get(address));
   }

   /**
    * After using a given BLE device, the app must call this method to ensure resources are
    * released properly.
    */
   public void close(BluetoothGatt gatt) {
       if (gatt == null) {
           return;
       }
       BluetoothGatt cGatt = mBluetoothGattHashMap.get(gatt.getDevice().getAddress());
       if (cGatt == null) {
           gatt.close();
       } else {
           cGatt.close();
           mBluetoothGattHashMap.remove(cGatt.getDevice().getAddress());
       }
   }




   /**
    * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
    * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
    * callback.
    *
    * @param characteristic The characteristic to read from.
    */
   public void readCharacteristic(BluetoothGattCharacteristic characteristic, String address) {
       if (mBluetoothAdapter == null || mBluetoothGattHashMap.get(address) == null) {
           return;
       }
       mBluetoothGattHashMap.get(address).readCharacteristic(characteristic);
   }

   /**
    * Request a write on a given {@code BluetoothGattCharacteristic}. The write result is reported
    * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
    * callback.
    *
    * @param characteristic The characteristic to write on.
    */
   public void writeCharacteristic(BluetoothGattCharacteristic characteristic, String address, byte[] value) {
       if (mBluetoothAdapter == null || mBluetoothGattHashMap.get(address) == null) {
           return;
       }
       characteristic.setValue(value);
       mBluetoothGattHashMap.get(address).writeCharacteristic(characteristic);
   }

   /**
    * Enables or disables notification on a give characteristic.
    *
    * @param characteristic Characteristic to act on.
    * @param enabled        If true, enable notification.  False otherwise.
    */
   public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                             String descriptorUuid, String address, boolean enabled) {
       if (mBluetoothAdapter == null || mBluetoothGattHashMap.get(address) == null) {
           return;
       }

       BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(descriptorUuid));
       if (descriptor != null) {
           descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
           mBluetoothGattHashMap.get(address).writeDescriptor(descriptor);
       }

       mBluetoothGattHashMap.get(address).setCharacteristicNotification(characteristic, enabled);
   }


   /**
    * Retrieves a list of supported GATT services on the connected device. This should be
    * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
    *
    * @return A {@code List} of supported services.
    */
   public List<BluetoothGattService> getSupportedGattServices(String address) {
       if (mBluetoothGattHashMap.get(address) == null) return null;

       return mBluetoothGattHashMap.get(address).getServices();
   }

   public BluetoothGattService getService(String address, UUID uuid) {
       if (mBluetoothGattHashMap.get(address) == null) return null;
       return mBluetoothGattHashMap.get(address).getService(uuid);
   }

   public List<BluetoothDevice> getConnectedDevices() {
       return mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER);
   }

   public void discoverServices(String address) {
       if (mBluetoothGattHashMap.get(address) == null) return;
       mBluetoothGattHashMap.get(address).discoverServices();
   }


    /**
     * get context
     * @return
     */
    public Context getContext(){
        return this;
    }
}
