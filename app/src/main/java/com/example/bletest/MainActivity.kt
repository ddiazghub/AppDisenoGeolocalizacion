package com.example.bletest

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.example.bletest.ble.ConnectionManager
import com.example.bletest.ble.ConnectionEventListener
import com.livinglifetechway.quickpermissions_kotlin.runWithPermissions
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.alert
import timber.log.Timber
import java.util.*


class MainActivity : AppCompatActivity() {

    val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"

    // Initializes Bluetooth adapter.
    private val adapter by lazy {
        val manager =
            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private val scanner by lazy {
        adapter.bluetoothLeScanner
    }

    private val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private var scanning = false
        set(value) {
            field = value
            runOnUiThread { scan_button.text = if (value) "Stop Scan" else "Start Scan" }
        }

    private var connected = false
    private val START_GATT_DELAY : Long = 500
    private val batteryServiceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    private val batteryLevelCharUuid = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    private var bluetoothGatt: BluetoothGatt? = null

    private val scanResults = mutableListOf<ScanResult>()
    private val scanResultAdapter: ScanResultAdapter by lazy {
        ScanResultAdapter(scanResults) { result ->
            // User tapped on a scan result
            if (scanning) {
                stopBLEScan()
            }
            with(result.device) {
                Timber.w("Connecting to $address")
                Run.after(START_GATT_DELAY) {
                    ConnectionManager.connect(this, this@MainActivity)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val UUID = expandUuid(0x180F)

        return_button.setOnClickListener {
            val intent = Intent(this, BLEConnect::class.java).apply {
                // Process
            }
            startActivity(intent)
        }

        scan_button.setOnClickListener {
            if (scanning) {
                stopBLEScan()
            } else {
                scanForDevices(this)
            }
        }

        setupRecyclerView()
        promptEnableBluetooth()
    }

    override fun onResume() {
        super.onResume()
        ConnectionManager.registerListener(connectionEventListener)
        if (!adapter.isEnabled) {
            promptEnableBluetooth()
        }
    }

    private fun promptEnableBluetooth() {
        if (adapter != null && !adapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            val REQUEST_ENABLE_BT = 1
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }
    private fun expandUuid(i: Int): UUID {
        return UUID.fromString("0000${i.toString(16)}-0000-1000-8000-00805f9b34fb")
    }

    fun scanForDevices(context: Context) {

        if (!adapter.isEnabled) {
            //handle error
        }

        // val uuid = ParcelUuid(serviceUUID)
        // val filter = ScanFilter.Builder().setServiceUuid(uuid).build()
        // val filters = listOf(filter)

        context.runWithPermissions(Manifest.permission.ACCESS_COARSE_LOCATION) {
            scanResults.clear()
            scanResultAdapter.notifyDataSetChanged()
            scanner.startScan(null, settings, callback)
            scanning = true
        }
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onConnectionSetupComplete = { gatt ->
                Intent(this@MainActivity, BLEConnect::class.java).also {
                    it.putExtra(BluetoothDevice.EXTRA_DEVICE, gatt.device)
                    startActivity(it)
                }
                ConnectionManager.unregisterListener(this)
            }
            onDisconnect = {
                runOnUiThread {
                    alert {
                        title = "Disconnected"
                        message = "Disconnected or unable to connect to device."
                        positiveButton("OK") {}
                    }.show()
                }
            }
        }
    }

    fun stopBLEScan() {
        scanner.stopScan(callback)
        scanning = false
    }

    fun disconnect() {
        bluetoothGatt?.getService(batteryServiceUuid)?.let { disableNotifications(
            it.getCharacteristic(
                batteryLevelCharUuid
            )
        ) }
        bluetoothGatt?.disconnect()
        connected = false
    }

    private val callback = object : ScanCallback() {
        /* override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { result ->
                // deviceFound(result.device)
            }
        }
        */

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
                scanResultAdapter.notifyItemChanged(indexQuery)
            } else {
                with(result.device) {
                    Timber.i("Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                }
                scanResults.add(result)
                scanResultAdapter.notifyItemInserted(scanResults.size - 1)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Timber.e("onScanFailed: code $errorCode")
        }
    }

    private fun setupRecyclerView() {
        scan_results_recycler_view.apply {
            adapter = scanResultAdapter
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false
        }

        val animator = scan_results_recycler_view.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }


    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("TimberArgCount")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Timber.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            "Successfully connected to $deviceAddress",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    bluetoothGatt = gatt
                    connected = true
                    device_tv.text = "Connected to: $deviceAddress"
                    Handler(Looper.getMainLooper()).post {

                        bluetoothGatt?.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Timber.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            "Successfully disconnected to $deviceAddress",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    device_tv.text = "Not connected"
                    gatt.close()
                }
            } else {
                Timber.w(
                    "BluetoothGattCallback",
                    "Error $status encountered for $deviceAddress! Disconnecting..."
                )
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        "Could not connect to device",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.w(
                    "BluetoothGattCallback",
                    "Discovered ${services.size} services for ${device.address}"
                )
                printGattTable() // See implementation just above this section
                // Consider connection setup as complete here
                val batteryLevelChar = gatt
                    .getService(batteryServiceUuid).getCharacteristic(batteryLevelCharUuid)

                enableNotifications(batteryLevelChar)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            with(characteristic) {
                Log.i(
                    "BluetoothGattCallback",
                    "Characteristic $uuid changed | value: ${value.toHexString()}"
                )
            }
        }
        /*
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "Read characteristic $uuid:\n${value.toHexString()}")
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status")
                    }
                }
            }
        }

        override fun onCharacteristicWrite(
                gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int
        ) { /* ... */}
        */
    }

    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i(
                "printGattTable",
                "No service and characteristic available, call discoverServices() first?"
            )
            return
        }
        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { it.uuid.toString() }
            Log.i(
                "printGattTable",
                "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
            )
        }
    }


    fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    fun BluetoothGattCharacteristic.isWritable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }

    fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

    fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

    /*
    private fun readBatteryLevel(gatt: BluetoothGatt) {
        val batteryServiceUuid = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val batteryLevelCharUuid = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val batteryLevelChar = gatt
            .getService(batteryServiceUuid)?.getCharacteristic(batteryLevelCharUuid)
        if (batteryLevelChar?.isReadable() == true) {
            gatt.readCharacteristic(batteryLevelChar)
        }
    }
    */

    fun ByteArray.toHexString(): String = joinToString(separator = " ", prefix = "0x")
        {
            String.format("%02X", it)
        }

    fun BluetoothGattDescriptor.isReadable(): Boolean =
        containsPermission(BluetoothGattDescriptor.PERMISSION_READ) ||
                containsPermission(BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED) ||
                containsPermission(BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM)
    fun BluetoothGattDescriptor.isWritable(): Boolean =
        containsPermission(BluetoothGattDescriptor.PERMISSION_WRITE) ||
                containsPermission(BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED) ||
                containsPermission(BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM) ||
                containsPermission(BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED) ||
                containsPermission(BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED_MITM)

    fun BluetoothGattDescriptor.containsPermission(permission: Int): Boolean =
        permissions and permission != 0

    private fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        bluetoothGatt?.let { gatt ->
            descriptor.value = payload
            gatt.writeDescriptor(descriptor)
        } ?: error("Not connected to a BLE device!")
    }

    fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
        val payload = when {
            characteristic.isIndicatable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> {
                Log.e(
                    "ConnectionManager",
                    "${characteristic.uuid} doesn't support notifications/indications"
                )
                return
            }
        }

        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (bluetoothGatt?.setCharacteristicNotification(characteristic, true) == false) {
                Log.e(
                    "ConnectionManager",
                    "setCharacteristicNotification failed for ${characteristic.uuid}"
                )
                return
            }
            writeDescriptor(cccDescriptor, payload)
        } ?: Log.e(
            "ConnectionManager",
            "${characteristic.uuid} doesn't contain the CCC descriptor!"
        )
    }

    fun disableNotifications(characteristic: BluetoothGattCharacteristic) {
        if (!characteristic.isNotifiable() && !characteristic.isIndicatable()) {
            Log.e(
                "ConnectionManager",
                "${characteristic.uuid} doesn't support indications/notifications"
            )
            return
        }

        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (bluetoothGatt?.setCharacteristicNotification(characteristic, false) == false) {
                Log.e(
                    "ConnectionManager",
                    "setCharacteristicNotification failed for ${characteristic.uuid}"
                )
                return
            }
            writeDescriptor(cccDescriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        } ?: Log.e(
            "ConnectionManager",
            "${characteristic.uuid} doesn't contain the CCC descriptor!"
        )
    }
}
