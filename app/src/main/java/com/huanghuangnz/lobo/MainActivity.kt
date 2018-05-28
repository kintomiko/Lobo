package com.huanghuangnz.lobo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.*
import com.huanghuangnz.lobo.listener.SpeechListener
import edu.cmu.pocketsphinx.Hypothesis
import java.lang.Exception


class MainActivity : Activity(), edu.cmu.pocketsphinx.RecognitionListener {

    override fun onResult(p0: Hypothesis?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onPartialResult(p0: Hypothesis?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onTimeout() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onBeginningOfSpeech() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onEndOfSpeech() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onError(p0: Exception?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    val TAG = MainActivity.javaClass.canonicalName

    lateinit var metTextHint: EditText
    private lateinit var mlvTextMatches: ListView
    private lateinit var msTextMatches: Spinner
    private lateinit var mSpeechRecognizer: SpeechRecognizer
    private lateinit var mSpeechIntent: Intent
    private lateinit var mWakeLock: PowerManager.WakeLock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

    }

    override fun onStart() {
        metTextHint = findViewById(R.id.etTextHint) as EditText
        mlvTextMatches = findViewById(R.id.lvTextMatches) as ListView
        msTextMatches = findViewById(R.id.sNoOfMatches) as Spinner

        checkVoiceRecognition()

        mSpeechIntent = getSpeechRecognizeIntent(false)
        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val speechListener = SpeechListener(mSpeechRecognizer, mSpeechIntent, metTextHint)
        mSpeechRecognizer.setRecognitionListener(speechListener)

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG)
        mWakeLock.acquire()
        mSpeechRecognizer.startListening(mSpeechIntent)

        super.onStart()
    }

    private fun checkVoiceRecognition() {
        // Check if voice recognition is present
        val pm = packageManager
        val activities = pm.queryIntentActivities(Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
        if (activities.size == 0) {
            Toast.makeText(this, "Voice recognizer not present", Toast.LENGTH_SHORT).show();
        }
//        val permissionCheck = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO)
//        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSIONS_REQUEST_RECORD_AUDIO)
//            return
//        }
    }

    private fun getSpeechRecognizeIntent(partialResult: Boolean = false): Intent {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)

        // Specify the calling package to identify your application
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, javaClass
                .`package`.name)

        // Given an hint to the recognizer about what the user is going to say
        //There are two form of language model available
        //1.LANGUAGE_MODEL_WEB_SEARCH : For short phrases
        //2.LANGUAGE_MODEL_FREE_FORM  : If not sure about the words or phrases and its domain.
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)

        // If number of Matches is not selected then return show toast message
        if (msTextMatches.selectedItemPosition == AdapterView.INVALID_POSITION) {
            Toast.makeText(this, "Please select No. of Matches from spinner",
                    Toast.LENGTH_SHORT).show()
            throw RuntimeException("Please select No. of Matches from spinner")
        }

        val noOfMatches = Integer.parseInt(msTextMatches.selectedItem
                .toString())
        // Specify how many results you want to receive. The results will be
        // sorted where the first result is the one with higher confidence.
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, noOfMatches)
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResult)
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 500)
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500)
        return intent
    }

    /**
     * Helper method to show the toast message
     **/
    private fun showToastMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {

        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("native-lib")
        }
    }
}
