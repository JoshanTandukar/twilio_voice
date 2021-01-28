package com.flutter.twilio.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.flutter.twilio.voice.`object`.callListener
import com.flutter.twilio.voice.`object`.registrationListener
import com.twilio.chat.ChannelListener
import com.twilio.chat.ChatClient
import com.twilio.chat.ErrorInfo
import com.twilio.chat.StatusListener
import com.twilio.voice.*
import com.twilio.voice.Call.Listener
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.*
import java.util.*


/** TwilioVoice */

const val TAG="TwilioVoice"
@Suppress("UNCHECKED_CAST")
class TwilioVoice: FlutterPlugin, ActivityAware{
    private lateinit var methodChannel: MethodChannel

    private lateinit var chatChannel: EventChannel

    private lateinit var mediaProgressChannel: EventChannel

    private lateinit var loggingChannel: EventChannel

    private lateinit var notificationChannel: EventChannel

    // This static function is optional and equivalent to onAttachedToEngine. It supports the old
    // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
    // plugin registration via this function while apps migrate to use the new Android APIs
    // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
    //
    // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
    // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
    // depending on the user's project. onAttachedToEngine or registerWith must both be defined
    // in the same class.
    companion object {
        @Suppress("unused")
        @JvmStatic
        fun registerWith(registrar: PluginRegistry.Registrar) 
        {
            instance = TwilioVoice()
            instance.onAttachedToEngine(registrar.context(), registrar.messenger())
            //TODO not need to here

            /*
         * Ensure the microphone permission is enabled
         */
        }

        lateinit var messenger: BinaryMessenger

        @JvmStatic
        lateinit var instance: TwilioVoice


        @JvmStatic
        lateinit var activity: Activity

        private const val MIC_PERMISSION_REQUEST_CODE = 1

        @JvmStatic
        var chatClient: ChatClient? = null

        private var activeCall: Call? = null

        private var activeCallInvite: CallInvite? = null

        private var cancelledCallIvites: CancelledCallInvite? = null

        var callListener: Listener = callListener()

        var registrationListener: RegistrationListener = registrationListener()

        val LOG_TAG = "TwilioVoice"

        var mediaProgressSink: EventChannel.EventSink? = null

        var loggingSink: EventChannel.EventSink? = null

        var notificationSink: EventChannel.EventSink? = null

        var handler = Handler(Looper.getMainLooper())

        var nativeDebug: Boolean = false

        var channelChannels: HashMap<String, EventChannel> = hashMapOf()
        var channelListeners: HashMap<String, ChannelListener> = hashMapOf()

        @JvmStatic
        fun debug(msg: String)
        {
            if (nativeDebug) {
                Log.d(LOG_TAG, msg)
                handler.post(Runnable {
                    loggingSink?.success(msg)
                })
            }
        }
    }

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        instance = this
        onAttachedToEngine(binding.applicationContext, binding.binaryMessenger)
    }

    private fun onAttachedToEngine(applicationContext: Context, messenger: BinaryMessenger) {
        TwilioVoice.messenger = messenger
        val pluginHandler = PluginHandler(applicationContext)
        methodChannel = MethodChannel(messenger, "TwilioVoice")
        methodChannel.setMethodCallHandler(pluginHandler)

        mediaProgressChannel = EventChannel(messenger, "TwilioVoice/media_progress")
        mediaProgressChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                debug("TwilioVoice.onAttachedToEngine => MediaProgress eventChannel attached")
                mediaProgressSink = events
            }

            override fun onCancel(arguments: Any?) {
                debug("TwilioVoice.onAttachedToEngine => MediaProgress eventChannel detached")
                mediaProgressSink = null
            }
        })

        loggingChannel = EventChannel(messenger, "TwilioVoice/logging")
        loggingChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                debug("TwilioVoice.onAttachedToEngine => Logging eventChannel attached")
                loggingSink = events
            }

            override fun onCancel(arguments: Any?) {
                debug("TwilioVoice.onAttachedToEngine => Logging eventChannel detached")
                loggingSink = null
            }
        })

        notificationChannel = EventChannel(messenger, "TwilioVoice/notification")
        notificationChannel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                debug("TwilioVoice.onAttachedToEngine => Notification eventChannel attached")
                notificationSink = events
            }

            override fun onCancel(arguments: Any) {
                debug("TwilioVoice.onAttachedToEngine => Notification eventChannel detached")
                notificationSink = null
            }
        })
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        debug("TwilioVoice.onDetachedFromEngine")
        methodChannel.setMethodCallHandler(null)
        chatChannel.setStreamHandler(null)
        loggingChannel.setStreamHandler(null)
        notificationChannel.setStreamHandler(null)
        mediaProgressChannel.setStreamHandler(null)
    }

    fun makeCall(call: MethodCall, result: MethodChannel.Result) {
        val to = call.argument<String>("To") ?: return result.error("ERROR", "Missing To", null)
        val from = call.argument<String>("from") ?: return result.error("ERROR", "Missing from", null)
        val accessToken = call.argument<String>("accessToken") ?: return result.error("ERROR", "Missing accessToken", null)
        val displayName = call.argument<String>("displayName") ?: return result.error("ERROR", "Missing display name", null)
        try
        {

            val params = HashMap<String, String>()
            params["To"] = to
            params["from"] = from
            params["accessToken"] = accessToken
            params["displayName"] = displayName
            val connectOptions = ConnectOptions.Builder(accessToken)
                    .params(params)
                    .build()
            activeCall = Voice.connect(activity, connectOptions, callListener)

        } catch (e: Exception) {
            result.error("ERROR", e.toString(), e)
        }
    }

    fun rejectCall(call: MethodCall, result: MethodChannel.Result)
    {
        activeCallInvite?.reject(activity)
    }

    fun handleMessage(call: MethodCall, result: MethodChannel.Result)
    {
        Log.d(TAG, "handleMessage kt: "+call.argument("notification"))
        val notification = call.argument("notification") as? Map<String, Any>

        val bundle=createBundleFromMap(notification)
        Voice.handleMessage(activity, bundle!!, object : MessageListener
        {
            override fun onCallInvite(callInvite: CallInvite)
            {
                Log.d(TAG, "onCallInvite: ")
                activeCallInvite = callInvite
            }

            override fun onCancelledCallInvite(cancelledCallInvite: CancelledCallInvite, @Nullable callException: CallException?)
            {
                Log.d(TAG, "onCancelledCallInvite: ")
                cancelledCallIvites = cancelledCallInvite
            }
        })
    }

    private fun createBundleFromMap(parameterMap: Map<String, Any>?): Bundle? {
        if (parameterMap == null) {
            return null
        }

        val bundle = Bundle()
        for (jsonParam in parameterMap.entries) {
            val value = jsonParam.value
            val key = jsonParam.key
            when (value) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
                is Double -> bundle.putDouble(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                is Map<*, *> -> {
                    val nestedBundle = createBundleFromMap(value as Map<String, Any>)
                    bundle.putBundle(key, nestedBundle as Bundle)
                }
                else -> throw IllegalArgumentException(
                        "Unsupported value type: $value")
            }
        }
        return bundle
    }

    fun acceptCall(call: MethodCall, result: MethodChannel.Result)
    {
        try
        {
            Log.d(TAG, "acceptCall: " + activeCallInvite!!.from)
            Log.d(TAG, "acceptCall: " + activeCallInvite!!.callerInfo)
            Log.d(TAG, "acceptCall: " + activeCallInvite!!.toString())
            activeCallInvite?.accept(activity, callListener)
        }
        catch (error: Exception)
        {
            Log.d(TAG, "acceptCall: " + error.message)
        }
    }

    fun registerForNotification(call: MethodCall, result: MethodChannel.Result)
    {
        val token: String = call.argument<String>("token") ?: return result.error("MISSING_PARAMS", "The parameter 'token' was not given", null)
        val accessToken: String = call.argument<String>("accessToken") ?: return result.error("MISSING_PARAMS", "The parameter 'accessToken' was not given", null)

        Voice.register(accessToken, Voice.RegistrationChannel.FCM, token, registrationListener)
    }

    fun unregisterForNotification(call: MethodCall, result: MethodChannel.Result) {
        val token: String = call.argument<String>("token") ?: return result.error("MISSING_PARAMS", "The parameter 'token' was not given", null)

        chatClient?.unregisterFCMToken(ChatClient.FCMToken(token), object : StatusListener() {
            override fun onSuccess() {
                debug("TwilioVoice.unregisterForNotification => unregistered with FCM $token")
                sendNotificationEvent("deregistered", mapOf("result" to true))
                result.success(null)
            }

            override fun onError(errorInfo: ErrorInfo?) {
                debug("TwilioVoice.unregisterForNotification => failed to unregister with FCM")
                super.onError(errorInfo)
                sendNotificationEvent("deregistered", mapOf("result" to false), errorInfo)
                result.error("FAILED", "Failed to unregister for FCM notifications", errorInfo)
            }
        })
    }

    private fun sendNotificationEvent(name: String, data: Any?, e: ErrorInfo? = null)
    {
        val eventData = mapOf("name" to name, "data" to data, "error" to Mapper.errorInfoToMap(e))
        notificationSink?.success(eventData)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity=binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        TODO("Not yet implemented")
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        TODO("Not yet implemented")
    }

    override fun onDetachedFromActivity() {
        TODO("Not yet implemented")
    }
}