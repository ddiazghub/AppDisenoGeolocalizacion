package com.example.bletest

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.StrictMode
import android.preference.PreferenceManager
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.bletest.ble.ConnectionEventListener
import com.example.bletest.ble.ConnectionManager
import com.example.bletest.ble.toHexString
import kotlinx.android.synthetic.main.activity_b_l_e_connect.*
import org.jetbrains.anko.alert
import timber.log.Timber
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.*

class BLEConnect : AppCompatActivity(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var tvGpsLocation: TextView
    private val locationPermissionCode = 2

    private val Settings = SocketOptions()

    private lateinit var device: BluetoothDevice
    private val characteristicUuid = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
    private var gasolineLevel: ByteArray? = null
    private var notifyingCharacteristics = mutableListOf<UUID>()
    private var characteristic: BluetoothGattCharacteristic? = null
    private val characteristics by lazy {
        ConnectionManager.servicesOnDevice(device)?.flatMap { service ->
            service.characteristics ?: listOf()
        } ?: listOf()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ConnectionManager.registerListener(connectionEventListener)
        super.onCreate(savedInstanceState)
        device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            ?: error("Missing BluetoothDevice from MainActivity!")

        setContentView(R.layout.activity_b_l_e_connect)

        ble_menu_button.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                // Process
            }
            startActivity(intent)
        }

        characteristics.forEach {
            if (it.uuid.equals(characteristicUuid)) {
                characteristic = it
                Run.after(1000) {
                    ConnectionManager.enableNotifications(device, it)
                }
                Run.after(2000) {
                    ConnectionManager.readCharacteristic(device, it)
                }
            }
        }

        ble_device_tv.text = "Connected to: ${device.name}"
        characteristic_tv.text = "Subscribed to: ${characteristic?.uuid}"

        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationPermissionCode)
        }

        //Se inicializan los objetos que interactúan con el usuario
        val button: Button = findViewById(R.id.getLocation)
        val editTextID: EditText = findViewById(R.id.editTextID)
        val editTextIP = findViewById<EditText>(R.id.editTextIP)
        val buttonID: Button = findViewById(R.id.buttonSaveID)

        //Se inicia a capturar la ubicación del dispositivo cuando se presiona el boton "Obtener ubicación"
        button.setOnClickListener {
            getLocation()
        }

        //Se guardan IP y ID para no tener que ser ingresados nuevamente
        buttonID.setOnClickListener {
            val pref = PreferenceManager.getDefaultSharedPreferences(this)
            val editor = pref.edit()

            editor
                    .putString("ID", editTextID.text.toString())
                    .putString("IP", editTextIP.text.toString())
                    .apply()

            val toast = Toast.makeText(applicationContext, "Guardado", Toast.LENGTH_LONG)
            toast.setGravity(Gravity.TOP, 0, 140)
            toast.show()
        }
        val pref = PreferenceManager.getDefaultSharedPreferences(this)
        pref.apply {
            val id = getString("ID", "")
            val ip = getString("IP", "")
            editTextID.setText(id)
            editTextIP.setText(ip)
        }
    }

    override fun onDestroy() {
        ConnectionManager.unregisterListener(connectionEventListener)
        ConnectionManager.teardownConnection(device)
        super.onDestroy()
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onDisconnect = {
                runOnUiThread {
                    alert {
                        title = "Disconnected"
                        message = "Disconnected from device."
                        positiveButton("OK") { onBackPressed() }
                    }.show()
                }
            }

            onCharacteristicRead = { _, characteristic ->
                Timber.w("Read ${characteristic.uuid}: ${characteristic.value.toHexString()}")
                runOnUiThread {
                    notification_tv.text = "Gasoline level: ${Integer.decode(characteristic.value.toHexString())}%"
                    /*
                    Toast.makeText(
                        applicationContext,
                        "Value changed on ${characteristic.uuid}: ${characteristic.value.toHexString()}",
                        Toast.LENGTH_SHORT
                    ).show()
                    */
                }
                gasolineLevel = characteristic.value
            }

            onCharacteristicWrite = { _, characteristic ->

            }

            onMtuChanged = { _, mtu ->

            }

            onCharacteristicChanged = { _, characteristic ->
                Timber.w("Value changed on ${characteristic.uuid}: ${characteristic.value.toHexString()}")
                runOnUiThread {
                    notification_tv.text = "Gasoline level: ${Integer.decode(characteristic.value.toHexString())}%"
                    /*
                    Toast.makeText(
                        applicationContext,
                        "Value changed on ${characteristic.uuid}: ${characteristic.value.toHexString()}",
                        Toast.LENGTH_SHORT
                    ).show()
                    */
                }
                gasolineLevel = characteristic.value
            }

            onNotificationsEnabled = { _, characteristic ->
                Timber.w("Enabled notifications on ${characteristic.uuid}")
                runOnUiThread {
                    Toast.makeText(
                            applicationContext,
                            "Enabled notifications on ${characteristic.uuid}",
                            Toast.LENGTH_SHORT
                    ).show()
                }
                notifyingCharacteristics.add(characteristic.uuid)
            }

            onNotificationsDisabled = { _, characteristic ->
                Timber.w("Disabled notifications on ${characteristic.uuid}")
                runOnUiThread {
                    Toast.makeText(
                            applicationContext,
                            "Disabled notifications on ${characteristic.uuid}",
                            Toast.LENGTH_SHORT
                    ).show()
                }
                notifyingCharacteristics.remove(characteristic.uuid)
            }
        }
    }

    private fun getLocation() {
        //Se inicializa un objeto de la clase location Manager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationPermissionCode)
        }
        //Se actualiza la ubicación del proveedor GPS cada vez que el usuario cambia su posición 5 metros y pasan 5 segundos.
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5f, this)
    }

    //Cuando se da un cambio en la ubicación actual se envía esta al servidor de datos.
    override fun onLocationChanged(location: Location) {
        val editTextID: EditText = findViewById(R.id.editTextID)
        tvGpsLocation = findViewById(R.id.textView)
        tvGpsLocation.textSize = 16F
        tvGpsLocation.text = "Latitude: ${location.latitude}\nLongitude: ${location.longitude}\nTimestamp: ${(location.time/1000).toInt()}"
        val editTextIP = findViewById<EditText>(R.id.editTextIP)
        Settings.setCustomHost(editTextIP.text.toString())
        var latitude = location.latitude.toFloat()
        var longitude = location.longitude.toFloat()
        var time = (location.time/1000).toInt()

        var id = editTextID.text
        var idBytes1 = hexToByte(id.subSequence(0, 2).toString())
        var idBytes2 = hexToByte(id.subSequence(2, 4).toString())
        var idBytes3 = hexToByte(id.subSequence(4, 6).toString())
        val byteBuffer = ByteBuffer.allocate(16)
        byteBuffer.put(idBytes1).put(idBytes2).put(idBytes3).putFloat(latitude).putFloat(longitude).putInt(time).put(gasolineLevel)
        var message = byteBuffer.array()
        sendUDP(message)
    }

    // Function to convert hex values to bytes.
    open fun hexToByte(hexString: String): Byte {
        val firstDigit = toDigit(hexString[0])
        val secondDigit = toDigit(hexString[1])
        return ((firstDigit shl 4) + secondDigit).toByte()
    }

    private fun toDigit(hexChar: Char): Int {
        val digit = Character.digit(hexChar, 16)
        require(digit != -1) { "Invalid Hexadecimal Character: $hexChar" }
        return digit
    }

    //Función para mostrar algo cuando se responde a una prompt de permisos de ubicación.
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location Permission Granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    //Función con la cual se crea el socket UDP y se envían los paquetes.
    fun sendUDP(message: ByteArray) {
        // Hack Prevent crash (sending should be done using an async task)
        val policy = StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
        try {
            //Open a port to send the package
            val socket = DatagramSocket()

            for (host in Settings.getHosts()) {
                var sendPacket = DatagramPacket(message, message.size, InetAddress.getByName(host), Settings.getPort())
                socket.send(sendPacket)
                runOnUiThread {
                    Toast.makeText(
                            applicationContext,
                            "Sent packet to $host",
                            Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: IOException) {
            runOnUiThread {
                Toast.makeText(
                        applicationContext,
                        "IO exception: ${e.message}",
                        Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}