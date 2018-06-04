package com.huanghuangnz.lobo.connectors

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import android.widget.Toast
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.Socket
import java.util.ArrayList
import java.util.HashMap

private const val UDP_HOST = "239.255.255.250"
private const val UDP_PORT = 1982
private const val searchMessage = "M-SEARCH * HTTP/1.1\r\n" +
        "HOST:239.255.255.250:1982\r\n" +
        "MAN:\"ssdp:discover\"\r\n" +
        "ST:wifi_bulb\r\n"
private const val MSG_SHOWLOG = 0
private const val MSG_FOUND_DEVICE = 1
private const val MSG_DISCOVER_FINISH = 2
private const val MSG_STOP_SEARCH = 3

private const val CMD_TOGGLE = "{\"id\":%id,\"method\":\"toggle\",\"params\":[]}\r\n"
private const val CMD_ON = "{\"id\":%id,\"method\":\"set_power\",\"params\":[\"on\",\"smooth\",500]}\r\n"
private const val CMD_OFF = "{\"id\":%id,\"method\":\"set_power\",\"params\":[\"off\",\"smooth\",500]}\r\n"

class YeelightConnector(val context: Context) : DeviceConnector{

    private val TAG = this.javaClass.canonicalName

    override fun init() {
        mAdapter = MyAdapter(context)
        mSearchThread = SearchThread()
    }

    override fun turn(on: Boolean) {
        if (on) {
            write(CMD_ON)
        } else {
            write(CMD_OFF)
        }
    }

    override fun toggle() {
        write(CMD_TOGGLE)
    }

    private var mDeviceList: MutableList<HashMap<String, String>> = ArrayList()
    lateinit var mAdapter: MyAdapter
    private var mSeraching = true
    private lateinit var mSearchThread: Thread
    private lateinit var mDSocket: DatagramSocket

    private lateinit var mSocket: Socket
    private lateinit var mBos: BufferedOutputStream
    private lateinit var mReader: BufferedReader

    private val mHandler = @SuppressLint("HandlerLeak")
    object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                MSG_FOUND_DEVICE -> mAdapter.notifyDataSetChanged()
                MSG_SHOWLOG -> Toast.makeText(this@YeelightConnector.context, "" + msg.obj.toString(), Toast.LENGTH_SHORT).show()
                MSG_STOP_SEARCH -> {
                    mSearchThread.interrupt()
                    mAdapter.notifyDataSetChanged()
                    mSeraching = false
                }
                MSG_DISCOVER_FINISH -> mAdapter.notifyDataSetChanged()
            }
        }
    }

    fun discover() {

        mDeviceList.clear()
        mAdapter.notifyDataSetChanged()
        mSeraching = true

        mSearchThread.start()

    }

    private inner class SearchThread : Thread(){
        override fun run() {
            try {
                mDSocket = DatagramSocket()
                val dpSend = DatagramPacket(searchMessage.toByteArray(),
                        searchMessage.toByteArray().size, InetAddress.getByName(UDP_HOST),
                        UDP_PORT)
                mDSocket.send(dpSend)
                mHandler.sendEmptyMessageDelayed(MSG_STOP_SEARCH, 2000)
                while (mSeraching) {
                    val buf = ByteArray(1024)
                    val dpRecv = DatagramPacket(buf, buf.size)
                    mDSocket.receive(dpRecv)
                    val bytes = dpRecv.data
                    val buffer = StringBuffer()
                    for (i in 0 until dpRecv.length) {
                        // parse /r
                        if (bytes[i].toInt() == 13) {
                            continue
                        }
                        buffer.append(bytes[i].toChar())
                    }
                    Log.d("socket", "got message:" + buffer.toString())
                    if (!buffer.toString().contains("yeelight")) {
                        mHandler.obtainMessage(MSG_SHOWLOG, "收到一条消息,不是Yeelight灯泡").sendToTarget()
                        return
                    }
                    val infos = buffer.toString().split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val bulbInfo = HashMap<String, String>()
                    for (str in infos) {
                        val index = str.indexOf(":")
                        if (index == -1) {
                            continue
                        }
                        val title = str.substring(0, index)
                        val value = str.substring(index + 1)
                        bulbInfo[title] = value
                    }
                    if (!hasAdd(bulbInfo)) {
                        mDeviceList.add(bulbInfo)
                    }

                }
                mHandler.sendEmptyMessage(MSG_DISCOVER_FINISH)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    inner class MyAdapter(context: Context) : BaseAdapter() {

        private val mLayoutInflater: LayoutInflater = LayoutInflater.from(context)
        private val mLayoutResource: Int = android.R.layout.simple_list_item_2

        override fun getCount(): Int {
            return mDeviceList.size
        }

        override fun getItem(position: Int): Any {
            return mDeviceList[position]
        }

        override fun getItemId(position: Int): Long {
            return 0
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view: View
            val data = getItem(position) as HashMap<String, String>
            if (convertView == null) {
                view = mLayoutInflater.inflate(mLayoutResource, parent, false)
            } else {
                view = convertView
            }
            val textView = view.findViewById(android.R.id.text1) as TextView
            textView.text = "Type = " + data["model"]

            Log.d(TAG, "name = " + textView.text.toString())
            val textSub = view.findViewById(android.R.id.text2) as TextView
            textSub.text = "location = " + data["Location"]
            return view
        }
    }

    private fun hasAdd(bulbinfo: HashMap<String, String>): Boolean {
        for (info in mDeviceList) {
            Log.d(TAG, "location params = " + bulbinfo["Location"])
            if (info["Location"] == bulbinfo["Location"]) {
                return true
            }
        }
        return false
    }

    fun write(cmd: String) {
        if (mBos != null && mSocket.isConnected) {
            try {
                mBos.write(cmd.toByteArray())
                mBos.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }

        } else {
            Log.d(TAG, "mBos = null or mSocket is closed")
        }
    }

    private var cmd_run = true

    fun connect(bulbInfo: HashMap<String, String>) {
        val ipinfo = bulbInfo["Location"]!!.split("//".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()[1]
        val ip = ipinfo.split(":".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()[0]
        val port = ipinfo.split(":".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()[1].toInt()
        Thread(Runnable {
            try {
                cmd_run = true
                mSocket = Socket(ip, port)
                mSocket.keepAlive = true
                mBos = BufferedOutputStream(mSocket.getOutputStream())
                mReader = BufferedReader(InputStreamReader(mSocket.getInputStream()))
                while (cmd_run) {
                    try {
                        val value = mReader.readLine()
                        Log.d(TAG, "value = $value")
                    } catch (e: Exception) {

                    }

                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }).start()
    }

    fun destroy(){
        try {
            cmd_run = false
            if (mSocket != null)
                mSocket.close()
        } catch (e: Exception) {

        }

    }

}

class YeelightConnectorListener {

}
