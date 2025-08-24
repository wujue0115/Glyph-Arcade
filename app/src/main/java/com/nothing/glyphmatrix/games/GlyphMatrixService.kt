package com.nothing.glyphmatrix.games

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy

abstract class GlyphMatrixService(private val tag: String) : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var specificSensor: Sensor? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(LOG_TAG, "$tag: onCreate - Initializing SensorManager")
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    open fun registerSpecificSensor(sensorType: Int, samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_NORMAL) {
        if (!::sensorManager.isInitialized) {
            Log.e(LOG_TAG, "$tag: SensorManager not initialized before attempting to register sensor.")
            return
        }
        specificSensor = sensorManager.getDefaultSensor(sensorType)
        specificSensor?.also { sensor ->
            val success = sensorManager.registerListener(this, sensor, samplingPeriodUs)
            if (success) {
                Log.d(LOG_TAG, "$tag: Successfully registered listener for ${sensor.name}")
            } else {
                Log.e(LOG_TAG, "$tag: FAILED to register listener for ${sensor.name}")
            }
        } ?: Log.w(LOG_TAG, "$tag: Sensor type $sensorType not available on this device.")
    }

    open fun unregisterSpecificSensor(sensorType: Int) {
        if (!::sensorManager.isInitialized) {
            Log.e(LOG_TAG, "$tag: SensorManager not initialized before attempting to unregister sensor.")
            return
        }
        specificSensor = sensorManager.getDefaultSensor(sensorType)
        specificSensor?.also { sensor ->
            sensorManager.unregisterListener(this, sensor)
            Log.d(LOG_TAG, "$tag: Unregistered listener for ${sensor.name}")
        } ?: Log.w(LOG_TAG, "$tag: Sensor type $sensorType not available on this device.")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LOG_TAG, "$tag: onDestroy - Unregistering sensor listener")
        sensorManager.unregisterListener(this) // 确保注销
    }

    private val buttonPressedHandler = object : Handler(Looper.getMainLooper()) {

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                GlyphToy.MSG_GLYPH_TOY -> {
                    msg.data?.let { data ->
                        if (data.containsKey(KEY_DATA)) {
                            data.getString(KEY_DATA)?.let { value ->
                                when (value) {
                                    GlyphToy.EVENT_ACTION_DOWN -> onTouchPointPressed()
                                    GlyphToy.EVENT_ACTION_UP -> onTouchPointReleased()
                                    GlyphToy.EVENT_CHANGE -> onTouchPointLongPress()
                                }
                            }
                        }
                    }
                }

                else -> {
                    Log.d(LOG_TAG, "Message: ${msg.what}")
                    super.handleMessage(msg)
                }
            }
        }
    }

    private val serviceMessenger = Messenger(buttonPressedHandler)

    var glyphMatrixManager: GlyphMatrixManager? = null
        private set

    private val gmmCallback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(p0: ComponentName?) {
            glyphMatrixManager?.let { gmm ->
                Log.d(LOG_TAG, "$tag: onServiceConnected")
                gmm.register(Glyph.DEVICE_23112)
                performOnServiceConnected(applicationContext, gmm)
            }
        }

        override fun onServiceDisconnected(p0: ComponentName?) {}
    }

    final override fun startService(intent: Intent?): ComponentName? {
        Log.d(LOG_TAG, "$tag: startService")
        return super.startService(intent)
    }

    final override fun onBind(intent: Intent?): IBinder? {
        Log.d(LOG_TAG, "$tag: onBind")
        GlyphMatrixManager.getInstance(applicationContext)?.let { gmm ->
            glyphMatrixManager = gmm
            gmm.init(gmmCallback)
            Log.d(LOG_TAG, "$tag: onBind completed")
        }
        return serviceMessenger.binder
    }

    final override fun onUnbind(intent: Intent?): Boolean {
        Log.d(LOG_TAG, "$tag: onUnbind")
        glyphMatrixManager?.let {
            Log.d(LOG_TAG, "$tag: onServiceDisconnected")
            performOnServiceDisconnected(applicationContext)
        }
        glyphMatrixManager?.turnOff()
        glyphMatrixManager?.unInit()
        glyphMatrixManager = null
        return false
    }

    open fun performOnServiceConnected(context: Context, glyphMatrixManager: GlyphMatrixManager) {}

    open fun performOnServiceDisconnected(context: Context) {}

    open fun onTouchPointPressed() {}
    open fun onTouchPointLongPress() {}
    open fun onTouchPointReleased() {}

    override fun onSensorChanged(event: SensorEvent?) {}

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        private val LOG_TAG = GlyphMatrixService::class.java.simpleName
        private const val KEY_DATA = "data"
    }

}