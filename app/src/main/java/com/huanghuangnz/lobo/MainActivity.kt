package com.huanghuangnz.lobo

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.widget.*
import edu.cmu.pocketsphinx.Assets
import edu.cmu.pocketsphinx.Hypothesis
import edu.cmu.pocketsphinx.SpeechRecognizerSetup
import java.io.File
import java.io.IOException
import java.lang.Exception
import java.lang.ref.WeakReference

private const val PERMISSIONS_REQUEST_RECORD_AUDIO = 1
class MainActivity : Activity(), edu.cmu.pocketsphinx.RecognitionListener {

    val TAG = this.javaClass.canonicalName

    private val kwSearch = "keyWord"
    private lateinit var metTextHint: TextView
    private lateinit var recognizer: edu.cmu.pocketsphinx.SpeechRecognizer

    override fun onResult(hypothesis: Hypothesis?) {
        Log.d(TAG, "onResult: ${hypothesis?.hypstr}")
    }

    override fun onPartialResult(hypothesis: Hypothesis?) {
        if (hypothesis == null) {
            return
        }
        Log.d(TAG, "onPartialResult: ${hypothesis.hypstr}")
        repeatSearch(hypothesis.hypstr)
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
                .setString("-mllr", File(assetsDir, "mllr_matrix").path)
                .setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .recognizer

        recognizer.addListener(this)

        // Create keyword-activation search.
        val keyWordFile = File(assetsDir, "keywords")
        recognizer.addKeywordSearch(kwSearch, keyWordFile)

//        val newsAsset = File(assetsDir, "zh_broadcastnews_64000_utf8.DMP")
//        recognizer.addNgramSearch("NEWS_SEARCH", newsAsset)

    }

    /**
     * Helper method to show the toast message
     **/
    private fun showToastMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

}
