package com.huanghuangnz.lobo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Message
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.widget.*
import com.huanghuangnz.lobo.connectors.GarageDoorOpenerConnector
import com.huanghuangnz.lobo.connectors.GarageDoorOpenerConnectListener
import com.huanghuangnz.lobo.connectors.YeelightConnector
import com.huanghuangnz.lobo.connectors.YeelightConnectorListener
import com.huanghuangnz.lobo.tts.VoiceSynthesizer
import com.huanghuangnz.lobo.voicerecognization.Action
import com.huanghuangnz.lobo.voicerecognization.CommandListener
import com.huanghuangnz.lobo.voicerecognization.VoiceCommandManager
import edu.cmu.pocketsphinx.Assets
import java.util.*

private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1

class MainActivity : Activity() {

    val TAG = this.javaClass.canonicalName

    //GUI components
    private lateinit var metTextHint: TextView
    private lateinit var mDevicesListView: ListView
    private lateinit var mDiscoverBtn: Button
    private lateinit var mSendA: Button
    private lateinit var mToggleLight: Button
    private lateinit var mBluetoothStatus: TextView
    private lateinit var mLightStatus: TextView
    private lateinit var mListView: ListView

    private val voiceSynthesizer = VoiceSynthesizer(this)

    private val yeelightConnector = YeelightConnector(this,
            object: YeelightConnectorListener {
                override fun onConnect(obj: HashMap<String, String>) {
                    mLightStatus.text = "Connected to Device: $obj"
                }
            }
    )

    private val garageDoorOpenerConnector = GarageDoorOpenerConnector(this,
            object : GarageDoorOpenerConnectListener {
                override fun onConnectionFailed(msg: Message) {
                    mBluetoothStatus.text = "Connection Failed"
                }
                override fun onConnect(obj: Any) {
                    mBluetoothStatus.text = "Connected to Device: $obj"
                }
                override fun onReadMessage(readMessage: String) {
                    mBluetoothStatus.text = "${mBluetoothStatus.text}readMessage"
                }
            }
    )

    private val XIAOAI_PACKAGENAME = "com.miui.voiceassist"

    private val START_XIAOAI_REQUEST_CODE: Int = Random().nextInt()

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == START_XIAOAI_REQUEST_CODE) {
            voiceRecognizerListener.switchSearch(voiceRecognizerListener.kwSearch)
        }
    }

    private val voiceRecognizerListener = VoiceCommandManager(
            object: CommandListener{
                override fun onWakeUp(assistant: Action) {
                    voiceSynthesizer.speak("我在")
                    val launchIntent = packageManager.getLaunchIntentForPackage(XIAOAI_PACKAGENAME)
                    if (launchIntent != null) {
                        startActivityForResult(launchIntent, START_XIAOAI_REQUEST_CODE)//null pointer check in case package name was not found
                    }
                }

                override fun onActionConfirm(action: Action) {
                    showToastMessage("正在执行：${action.name}")
                    when {
                        action.name.contains("开门") -> garageDoorOpenerConnector.sendMessage("A")
                        action.name.contains("关门") -> garageDoorOpenerConnector.sendMessage("A")
                        action.name.contains("开灯") -> yeelightConnector.turn(true)
                        action.name.contains("关灯") -> yeelightConnector.turn(false)
                    }
                }

                override fun onListening(){
                    metTextHint.text = "萝卜待命中。。。请随时指示"
                }

                override fun onErrorSetup() {
                    metTextHint.text = "failed to start voice recognizer"
                }

                override fun onPendingConfirm(keyWord: Action) {
                    metTextHint.text = "请说出密码来确认执行: ${keyWord.name}"
                    showToastMessage("暗号是？")
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
        garageDoorOpenerConnector.init()
        voiceSynthesizer.init()
        yeelightConnector.init()

        mDevicesListView = findViewById(R.id.devicesListView) as ListView
        mDevicesListView.adapter = garageDoorOpenerConnector.mBTArrayAdapter // assign model to view
        mDevicesListView.onItemClickListener = mDeviceClickListener
        mBluetoothStatus = findViewById(R.id.bluetoothStatus) as TextView
        mLightStatus = findViewById(R.id.lightStatus) as TextView
        mDiscoverBtn = findViewById(R.id.discover) as Button
        mSendA = findViewById(R.id.sendA) as Button
        mToggleLight = findViewById(R.id.toggleLight) as Button

        mDiscoverBtn.setOnClickListener({ v ->
            garageDoorOpenerConnector.discover(v)
            yeelightConnector.discover()
        })

        mSendA.setOnClickListener({
            garageDoorOpenerConnector.sendMessage("A")
        })

        mToggleLight.setOnClickListener({
            yeelightConnector.toggle()
        })

        mListView = findViewById(R.id.lightList) as ListView
        mListView.adapter = yeelightConnector.mAdapter
        mListView.onItemClickListener = AdapterView.OnItemClickListener { parent, view, position, id ->
            yeelightConnector.connect(yeelightConnector.mAdapter.getItem(position) as HashMap<String, String>)
        }
    }

    private val mDeviceClickListener = AdapterView.OnItemClickListener { av, v, arg2, arg3 ->
        if (!garageDoorOpenerConnector.enabled()) {
            Toast.makeText(baseContext, "Bluetooth not on", Toast.LENGTH_SHORT).show()
            return@OnItemClickListener
        }

        mBluetoothStatus.text = "Connecting..."
        // Get the device MAC address, which is the last 17 chars in the View
        val info = (v as TextView).text.toString()
        val address = info.substring(info.length - 17)
        val name = info.substring(0, info.length - 17)

        garageDoorOpenerConnector.asyncConnect(address, name)

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
        yeelightConnector.destroy()
        garageDoorOpenerConnector.destory()
    }

    /**
     * Helper method to show the toast message
     **/
    private fun showToastMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        voiceSynthesizer.speak(message)
    }

}
