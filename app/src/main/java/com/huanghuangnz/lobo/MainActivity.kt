package com.huanghuangnz.lobo

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Message
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.widget.*
import com.huanghuangnz.lobo.bluetooth.BluetoothAsyncManager
import com.huanghuangnz.lobo.bluetooth.BluetoothEventListener
import com.huanghuangnz.lobo.tts.VoiceSynthesizer
import com.huanghuangnz.lobo.voicerecognization.CommandListener
import com.huanghuangnz.lobo.voicerecognization.VoiceCommandManager
import edu.cmu.pocketsphinx.Assets
import java.io.File

private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1

class MainActivity : Activity() {

    val TAG = this.javaClass.canonicalName

    private fun media(file: File): MediaPlayer{
        return MediaPlayer().let {
            it.setDataSource(file.path)
            it.prepare()
            it
        }
    }


    //GUI components
    private lateinit var metTextHint: TextView
    private lateinit var mDevicesListView: ListView
    private lateinit var mDiscoverBtn: Button
    private lateinit var mListPairedDevicesBtn: Button
    private lateinit var mSendA: Button
    private lateinit var mBluetoothStatus: TextView
    private lateinit var mReadBuffer: TextView

    private val voiceSynthesizer = VoiceSynthesizer(this)

    private val btConnectManager = BluetoothAsyncManager(this,
            object: BluetoothEventListener{
                override fun onConnectionFailed(msg: Message) {
                    mBluetoothStatus.text = "Connection Failed"
                }

                override fun onConnect(obj: Any) {
                    mBluetoothStatus.text = "Connected to Device: $obj"
                }

                override fun onReadMessage(readMessage: String) {
                    mReadBuffer.text = readMessage
                }

            }

    )

    private val voiceRecognizerListener = VoiceCommandManager(
            object: CommandListener{
                override fun onWakeUp(assistant: String) {
                    showToastMessage("现在是${assistant}为您服务")
                }

                override fun onActionConfirm(action: String) {
                    showToastMessage("正在执行：$action")
                    if (action.contains("开门")) {
                        btConnectManager.sendMessage("A")
                    }
                }

                override fun onListening(){
                    metTextHint.text = "萝卜待命中。。。请随时指示"
                }

                override fun onErrorSetup() {
                    metTextHint.text = "failed to start voice recognizer"
                }

                override fun onAction(keyWord: String) {
                    val msg = "请说出密码来确认执行: $keyWord"
                    metTextHint.text = msg
                    showToastMessage(msg)
                }
            }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //init voice recognizer
        setContentView(R.layout.activity_main)
        metTextHint = findViewById(R.id.etTextHint) as TextView
        metTextHint.text = "Preparing the recognizer"

        // Check if user has given permission to record audio
        val permissionCheckRecord = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO)
        val permissionCheckBT = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH_ADMIN)
        val permissionCheckBTA = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.BLUETOOTH)
        if (permissionCheckRecord != PackageManager.PERMISSION_GRANTED ||
                permissionCheckBT != PackageManager.PERMISSION_GRANTED ||
                permissionCheckBTA != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN),
                    PERMISSIONS_REQUEST_RECORD_AUDIO)
            return
        }

        voiceRecognizerListener.init(Assets(this).syncAssets())
        btConnectManager.init()
        voiceSynthesizer.init()

        mDevicesListView = findViewById(R.id.devicesListView) as ListView
        mDevicesListView.adapter = btConnectManager.mBTArrayAdapter // assign model to view
        mDevicesListView.onItemClickListener = mDeviceClickListener
        mBluetoothStatus = findViewById(R.id.bluetoothStatus) as TextView
        mDiscoverBtn = findViewById(R.id.discover) as Button
        mListPairedDevicesBtn = findViewById(R.id.PairedBtn) as Button
        mReadBuffer = findViewById(R.id.readBuffer) as TextView
        mSendA = findViewById(R.id.sendA) as Button

        mDiscoverBtn.setOnClickListener({ v -> btConnectManager.discover(v) })

        mListPairedDevicesBtn.setOnClickListener({ v -> btConnectManager.listPairedDevices(v) })

        mSendA.setOnClickListener({
            btConnectManager.sendMessage("A")
        })
    }

    private val mDeviceClickListener = AdapterView.OnItemClickListener { av, v, arg2, arg3 ->
        if (!btConnectManager.enabled()) {
            Toast.makeText(baseContext, "Bluetooth not on", Toast.LENGTH_SHORT).show()
            return@OnItemClickListener
        }

        mBluetoothStatus.text = "Connecting..."
        // Get the device MAC address, which is the last 17 chars in the View
        val info = (v as TextView).text.toString()
        val address = info.substring(info.length - 17)
        val name = info.substring(0, info.length - 17)

        btConnectManager.asyncConnect(address, name)

    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                voiceRecognizerListener.init(Assets(this).syncAssets())
            } else {
                finish()
            }
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        voiceRecognizerListener.destroy()
    }

    /**
     * Helper method to show the toast message
     **/
    private fun showToastMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        voiceSynthesizer.speak(message)
    }


}
