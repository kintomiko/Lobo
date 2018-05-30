package com.huanghuangnz.lobo

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.View
import android.widget.*
import edu.cmu.pocketsphinx.Assets
import edu.cmu.pocketsphinx.Hypothesis
import edu.cmu.pocketsphinx.SpeechRecognizerSetup
import java.io.*
import java.lang.Exception
import java.lang.ref.WeakReference
import java.nio.charset.Charset
import java.util.*

private val BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // "random" unique identifier
private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1
private const val REQUEST_ENABLE_BT = 1 // used to identify adding bluetooth names
private const val MESSAGE_READ = 2 // used in bluetooth handler to identify message update
private const val CONNECTING_STATUS = 3 // used in bluetooth handler to identify message status

class MainActivity : Activity(), edu.cmu.pocketsphinx.RecognitionListener {

    val TAG = this.javaClass.canonicalName

    private val kwSearch = "keyWord"


    //SpeechRecognizer components
    private lateinit var recognizer: edu.cmu.pocketsphinx.SpeechRecognizer

    //Bluetooth components
    private lateinit var mBTAdapter: BluetoothAdapter
    private lateinit var mBTArrayAdapter: ArrayAdapter<String>
    private lateinit var mPairedDevices: Set<BluetoothDevice>
    private lateinit var mBTSocket: BluetoothSocket
    private lateinit var mConnectedThread: ConnectedThread // bluetooth background worker thread to send and receive data

    //GUI components
    private lateinit var mDevicesListView: ListView
    private lateinit var metTextHint: TextView
    private lateinit var mDiscoverBtn: Button
    private lateinit var mListPairedDevicesBtn: Button
    private lateinit var mSendA: Button
    private lateinit var mBluetoothStatus: TextView
    private lateinit var mReadBuffer: TextView

    //Controller components
    private lateinit var mHandler: Handler

    override fun onResult(hypothesis: Hypothesis?) {
        Log.d(TAG, "onResult: ${hypothesis?.hypstr}")
    }

    override fun onPartialResult(hypothesis: Hypothesis?) {
        if (hypothesis == null) {
            return
        }
        Log.d(TAG, "onPartialResult: ${hypothesis.hypstr}")
        repeatSearch(hypothesis.hypstr)
        if ( hypothesis.hypstr.trim().contains("芝麻开门") ){
            if (mConnectedThread != null)
                mConnectedThread.write("A")
        }
    }

    private fun repeatSearch(hypstr: String) {
        recognizer.stop()
        recognizer.startListening(kwSearch)

        (findViewById(R.id.etTextHint) as TextView).text = hypstr
    }

    override fun onTimeout() {
        Log.d(TAG, "onTimeout")
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "onBeginningOfSpeech")
    }

    override fun onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech")
    }

    override fun onError(p0: Exception?) {
        Log.d(TAG, "onError")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        metTextHint = findViewById(R.id.etTextHint) as TextView
        metTextHint.text = "Preparing the recognizer"

        // Check if user has given permission to record audio
        val permissionCheck = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO)
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSIONS_REQUEST_RECORD_AUDIO)
            return
        }
        SetupTask(this).execute()

        mBTArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)

        mDevicesListView = findViewById(R.id.devicesListView) as ListView
        mDevicesListView.adapter = mBTArrayAdapter // assign model to view
        mDevicesListView.onItemClickListener = mDeviceClickListener
        mBluetoothStatus = findViewById(R.id.bluetoothStatus) as TextView
        mDiscoverBtn = findViewById(R.id.discover) as Button
        mListPairedDevicesBtn = findViewById(R.id.PairedBtn) as Button
        mReadBuffer = findViewById(R.id.readBuffer) as TextView
        mSendA = findViewById(R.id.sendA) as Button

        mBTAdapter = BluetoothAdapter.getDefaultAdapter() // get a handle on the bluetooth radio
        mDiscoverBtn.setOnClickListener(View.OnClickListener { v -> discover(v) })
        mHandler = object : Handler() {
            override fun handleMessage(msg: android.os.Message) {
                if (msg.what == MESSAGE_READ) {
                    var readMessage: String? = null
                    try {
                        readMessage = String(msg.obj as ByteArray, Charset.forName("UTF-8"))
                    } catch (e: UnsupportedEncodingException) {
                        e.printStackTrace()
                    }

                    mReadBuffer.setText(readMessage)
                }

                if (msg.what == CONNECTING_STATUS) {
                    if (msg.arg1 == 1)
                        mBluetoothStatus.text = "Connected to Device: " + msg.obj as String
                    else
                        mBluetoothStatus.text = "Connection Failed"
                }
            }
        }

        mListPairedDevicesBtn.setOnClickListener(View.OnClickListener { v -> listPairedDevices(v) })

        mSendA.setOnClickListener(View.OnClickListener {
            if (mConnectedThread != null)
            //First check to make sure thread created
                mConnectedThread.write("A")
        })
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

    private val mDeviceClickListener = AdapterView.OnItemClickListener { av, v, arg2, arg3 ->
        if (!mBTAdapter.isEnabled) {
            Toast.makeText(baseContext, "Bluetooth not on", Toast.LENGTH_SHORT).show()
            return@OnItemClickListener
        }

        mBluetoothStatus.setText("Connecting...")
        // Get the device MAC address, which is the last 17 chars in the View
        val info = (v as TextView).text.toString()
        val address = info.substring(info.length - 17)
        val name = info.substring(0, info.length - 17)

        // Spawn a new thread to avoid blocking the GUI one
        object : Thread() {
            override fun run() {
                var fail = false

                val device = mBTAdapter.getRemoteDevice(address)

                try {
                    mBTSocket = createBluetoothSocket(device)
                } catch (e: IOException) {
                    fail = true
                    Toast.makeText(baseContext, "Socket creation failed", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(baseContext, "Socket creation failed", Toast.LENGTH_SHORT).show()
                    }

                }

                if (fail == false) {
                    mConnectedThread = ConnectedThread(mBTSocket)
                    mConnectedThread.start()

                    mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                            .sendToTarget()
                }
            }
        }.start()
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

    private fun listPairedDevices(view: View) {
        mPairedDevices = mBTAdapter.bondedDevices
        if (mBTAdapter.isEnabled) {
            // put it's one to the adapter
            for (device in mPairedDevices)
                mBTArrayAdapter.add(device.name + "\n" + device.address)

            Toast.makeText(applicationContext, "Show Paired Devices", Toast.LENGTH_SHORT).show()
        } else
            Toast.makeText(applicationContext, "Bluetooth not on", Toast.LENGTH_SHORT).show()
    }

    private fun discover(view: View) {
        // Check if the device is already discovering
        if (mBTAdapter.isDiscovering()) {
            mBTAdapter.cancelDiscovery()
            Toast.makeText(applicationContext, "Discovery stopped", Toast.LENGTH_SHORT).show()
        } else {
            if (mBTAdapter.isEnabled()) {
                mBTArrayAdapter.clear() // clear items
                registerReceiver(blReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
                mBTAdapter.startDiscovery()
                Toast.makeText(applicationContext, "Discovery started", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(applicationContext, "Bluetooth not on", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                SetupTask(this).execute()
            } else {
                finish()
            }
        }
    }

    public override fun onDestroy() {
        super.onDestroy()

        if (recognizer != null) {
            recognizer.cancel()
            recognizer.shutdown()
        }
    }

    inner class SetupTask internal constructor(activity: MainActivity) : AsyncTask<Void, Void, Exception>() {
        private var activityReference: WeakReference<MainActivity> = WeakReference(activity)

        override fun doInBackground(vararg params: Void): Exception? {
            try {
                val assets = Assets(activityReference.get()!!)
                val assetDir = assets.syncAssets()
                activityReference.get()!!.setupRecognizer(assetDir)
            } catch (e: IOException) {
                return e
            }

            return null
        }

        override fun onPostExecute(result: Exception?) {
            if (result != null) {
                (activityReference.get()!!.findViewById(R.id.etTextHint) as TextView).text = "Failed to init recognizer $result"
            } else {
                activityReference.get()!!.switchSearch(kwSearch)
            }
        }
    }

    private fun switchSearch(searchName: String) {
        recognizer.stop()
        recognizer.startListening(searchName)

        (findViewById(R.id.etTextHint) as TextView).text = kwSearch
    }

    @Throws(IOException::class)
    private fun setupRecognizer(assetsDir: File) {

        recognizer = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(File(assetsDir, "cn-zh-ptm"))
                .setDictionary(File(assetsDir, "zh_broadcastnews_utf8.dic"))
                .recognizer

        recognizer.addListener(this)

        // Create keyword-activation search.
        val keyWordFile = File(assetsDir, "keywords")
        recognizer.addKeywordSearch(kwSearch, keyWordFile)

    }

    /**
     * Helper method to show the toast message
     **/
    private fun showToastMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

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
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
            }

        }
    }

}
