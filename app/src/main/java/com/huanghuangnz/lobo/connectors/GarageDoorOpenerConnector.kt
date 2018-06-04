package com.huanghuangnz.lobo.connectors

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import java.lang.Exception
import java.nio.charset.Charset
import java.util.*

private val BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // "random" unique identifier
private const val MESSAGE_READ = 2 // used in bluetooth handler to identify message update
private const val CONNECTING_STATUS = 3 // used in bluetooth handler to identify message status
class GarageDoorOpenerConnector constructor(val context: Context, garageDoorOpenerConnectListener: GarageDoorOpenerConnectListener): DeviceConnector{
    override fun turn(on: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun toggle() {
        sendMessage("A")
    }

    private val TAG = this.javaClass.canonicalName
    //Bluetooth components
    private lateinit var mBTAdapter: BluetoothAdapter
    lateinit var mBTArrayAdapter: ArrayAdapter<String>
    private lateinit var mPairedDevices: Set<BluetoothDevice>
    private lateinit var mBTSocket: BluetoothSocket
    private var mConnectedThread: ConnectedThread? = null // bluetooth background worker thread to send and receive data

    override fun init(){
        mBTArrayAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_1)
        mBTAdapter = BluetoothAdapter.getDefaultAdapter() // get a handle on the bluetooth radio
    }

    private val mHandler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: android.os.Message) {
            if (msg.what == MESSAGE_READ) {
                var readMessage: String
                try {
                    readMessage = String(msg.obj as ByteArray, Charset.forName("UTF-8"))
                } catch (e: UnsupportedEncodingException) {
                    throw RuntimeException(e)
                }

                garageDoorOpenerConnectListener.onReadMessage(readMessage)
            }

            if (msg.what == CONNECTING_STATUS) {
                if (msg.arg1 == 1)
                    garageDoorOpenerConnectListener.onConnect(msg.obj)
                else
                    garageDoorOpenerConnectListener.onConnectionFailed(msg)
            }
        }
    }

    private val blReceiver: BroadcastReceiver = object: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "get intent ${intent.action}")
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                // add the name to the list
                Log.d(TAG, "discovered device: ${device.name}")
                mBTArrayAdapter.add(device.name + "\n" + device.address)
                mBTArrayAdapter.notifyDataSetChanged()
            }
        }
    }

    fun listPairedDevices(view: View) {
        mPairedDevices = mBTAdapter.bondedDevices
        if (mBTAdapter.isEnabled) {
            for (device in mPairedDevices)
                mBTArrayAdapter.add(device.name + "\n" + device.address)

            Toast.makeText(context, "Show Paired Devices", Toast.LENGTH_SHORT).show()
        } else
            Toast.makeText(context, "Bluetooth not on", Toast.LENGTH_SHORT).show()
    }

    fun discover(view: View) {
        // Check if the device is already discovering
        if (mBTAdapter.isDiscovering) {
            mBTAdapter.cancelDiscovery()
            Toast.makeText(context, "Discovery stopped", Toast.LENGTH_SHORT).show()
        } else {
            if (mBTAdapter.isEnabled) {
                mBTArrayAdapter.clear() // clear items
                context.registerReceiver(blReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
                mBTAdapter.startDiscovery()
                Toast.makeText(context, "Discovery started", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Bluetooth not on", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun destory(){
        if (mBTAdapter.isDiscovering) {
            mBTAdapter.cancelDiscovery()
        }
        mConnectedThread?.destory()
    }

    @Throws(IOException::class)
    private fun createBluetoothSocket(device: BluetoothDevice): BluetoothSocket {
        try {
            val m = device.javaClass.getMethod("createInsecureRfcommSocketToServiceRecord", UUID::class.java)
            return m.invoke(device, BTMODULEUUID) as BluetoothSocket
        } catch (e: Exception) {
            Log.e(TAG, "Could not create Insecure RFComm Connection", e)
        }

        return device.createRfcommSocketToServiceRecord(BTMODULEUUID)
    }

    fun asyncConnect(address: String, name: String) {
        object : Thread() {
            override fun run() {
                var fail = false

                val device = mBTAdapter.getRemoteDevice(address)

                try {
                    mBTSocket = createBluetoothSocket(device)
                } catch (e: IOException) {
                    fail = true
                    Toast.makeText(context, "Socket creation failed", Toast.LENGTH_SHORT).show()
                }

                // Establish the Bluetooth socket connection.
                try {
                    mBTSocket.connect()
                } catch (e: IOException) {
                    try {
                        fail = true
                        mBTSocket.close()
                        mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                .sendToTarget()
                    } catch (e2: IOException) {
                        //insert code to deal with this
                        Toast.makeText(context, "Socket creation failed", Toast.LENGTH_SHORT).show()
                    }

                }

                if (!fail) {
                    mConnectedThread = ConnectedThread(mBTSocket)
                    mConnectedThread!!.start()

                    mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                            .sendToTarget()
                }
            }
        }.start()
    }

    fun sendMessage(message: String) {
        if (mConnectedThread != null) {
            mConnectedThread!!.write(message)
        }
    }

    fun enabled(): Boolean = mBTAdapter.isEnabled

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?

        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = mmSocket.inputStream
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
            }

            mmInStream = tmpIn
            mmOutStream = tmpOut
        }

        override fun run() {
            var buffer = ByteArray(1024)  // buffer store for the stream
            var bytes: Int // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream!!.available()
                    if (bytes != 0) {
                        buffer = ByteArray(1024)
                        SystemClock.sleep(100) //pause and wait for rest of data. Adjust this depending on your sending speed.
                        bytes = mmInStream.available() // how many bytes are ready to be read?
                        bytes = mmInStream.read(buffer, 0, bytes) // record how many bytes we actually read
                        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget() // Send the obtained bytes to the UI activity
                    }
                } catch (e: IOException) {
                    e.printStackTrace()

                    break
                }

            }
        }

        /* Call this from the main activity to send data to the remote device */
        fun write(input: String) {
            val bytes = input.toByteArray()           //converts entered String into bytes
            try {
                mmOutStream!!.write(bytes)
            } catch (e: IOException) {
            }

        }

        /* Call this from the main activity to shutdown the connection */
        fun destory() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
            }

        }
    }

}

interface GarageDoorOpenerConnectListener {
    fun onReadMessage(readMessage: String)
    fun onConnect(obj: Any)
    fun onConnectionFailed(msg: Message)

}
