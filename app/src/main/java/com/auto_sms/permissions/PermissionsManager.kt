package com.auto_sms.permissions

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener

class PermissionsManager(reactContext: ReactApplicationContext) : 
    ReactContextBaseJavaModule(reactContext), PermissionListener {
    
    private var mPromise: Promise? = null
    private var mRequestCode = 0
    
    companion object {
        private const val MODULE_NAME = "PermissionsManager"
        private const val E_ACTIVITY_DOES_NOT_EXIST = "E_ACTIVITY_DOES_NOT_EXIST"
        private const val E_PERMISSION_DENIED = "E_PERMISSION_DENIED"
        
        // Permission request codes
        private const val REQUEST_CODE_READ_CALL_LOG = 1
        private const val REQUEST_CODE_CALL_PHONE = 2
        private const val REQUEST_CODE_ANSWER_PHONE_CALLS = 3
        private const val REQUEST_CODE_READ_CONTACTS = 4
        private const val REQUEST_CODE_SEND_SMS = 5
        private const val REQUEST_CODE_READ_SMS = 6
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 7
    }

    override fun getName(): String {
        return MODULE_NAME
    }

    @ReactMethod
    fun checkPermission(permission: String, promise: Promise) {
        val activity = currentActivity
        if (activity == null) {
            promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist")
            return
        }

        when (permission) {
            "READ_CALL_LOG" -> {
                val hasPermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CALL_LOG) == 
                    PackageManager.PERMISSION_GRANTED
                promise.resolve(hasPermission)
            }
            "CALL_PHONE" -> {
                val hasPermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.CALL_PHONE) == 
                    PackageManager.PERMISSION_GRANTED
                promise.resolve(hasPermission)
            }
            "ANSWER_PHONE_CALLS" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val hasPermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.ANSWER_PHONE_CALLS) == 
                        PackageManager.PERMISSION_GRANTED
                    promise.resolve(hasPermission)
                } else {
                    promise.resolve(false)
                }
            }
            "READ_CONTACTS" -> {
                val hasPermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CONTACTS) == 
                    PackageManager.PERMISSION_GRANTED
                promise.resolve(hasPermission)
            }
            "SEND_SMS" -> {
                val hasPermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.SEND_SMS) == 
                    PackageManager.PERMISSION_GRANTED
                promise.resolve(hasPermission)
            }
            "READ_SMS" -> {
                val hasPermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_SMS) == 
                    PackageManager.PERMISSION_GRANTED
                promise.resolve(hasPermission)
            }
            "POST_NOTIFICATIONS" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val hasPermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == 
                        PackageManager.PERMISSION_GRANTED
                    promise.resolve(hasPermission)
                } else {
                    promise.resolve(true) // Notification permission automatically granted for Android < 13
                }
            }
            else -> promise.reject("INVALID_PERMISSION", "Invalid permission type: $permission")
        }
    }

    @ReactMethod
    fun requestPermission(permission: String, promise: Promise) {
        val activity = currentActivity as? PermissionAwareActivity
            ?: run {
                promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist or is not PermissionAwareActivity")
                return
            }

        mPromise = promise

        when (permission) {
            "READ_CALL_LOG" -> {
                mRequestCode = REQUEST_CODE_READ_CALL_LOG
                activity.requestPermissions(
                    arrayOf(Manifest.permission.READ_CALL_LOG),
                    REQUEST_CODE_READ_CALL_LOG,
                    this
                )
            }
            "CALL_PHONE" -> {
                mRequestCode = REQUEST_CODE_CALL_PHONE
                activity.requestPermissions(
                    arrayOf(Manifest.permission.CALL_PHONE),
                    REQUEST_CODE_CALL_PHONE,
                    this
                )
            }
            "ANSWER_PHONE_CALLS" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mRequestCode = REQUEST_CODE_ANSWER_PHONE_CALLS
                    activity.requestPermissions(
                        arrayOf(Manifest.permission.ANSWER_PHONE_CALLS),
                        REQUEST_CODE_ANSWER_PHONE_CALLS,
                        this
                    )
                } else {
                    promise.resolve(false)
                }
            }
            "READ_CONTACTS" -> {
                mRequestCode = REQUEST_CODE_READ_CONTACTS
                activity.requestPermissions(
                    arrayOf(Manifest.permission.READ_CONTACTS),
                    REQUEST_CODE_READ_CONTACTS,
                    this
                )
            }
            "SEND_SMS" -> {
                mRequestCode = REQUEST_CODE_SEND_SMS
                activity.requestPermissions(
                    arrayOf(Manifest.permission.SEND_SMS),
                    REQUEST_CODE_SEND_SMS,
                    this
                )
            }
            "READ_SMS" -> {
                mRequestCode = REQUEST_CODE_READ_SMS
                activity.requestPermissions(
                    arrayOf(Manifest.permission.READ_SMS),
                    REQUEST_CODE_READ_SMS,
                    this
                )
            }
            "POST_NOTIFICATIONS" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mRequestCode = REQUEST_CODE_POST_NOTIFICATIONS
                    activity.requestPermissions(
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        REQUEST_CODE_POST_NOTIFICATIONS,
                        this
                    )
                } else {
                    promise.resolve(true) // Notification permission automatically granted for Android < 13
                }
            }
            else -> promise.reject("INVALID_PERMISSION", "Invalid permission type: $permission")
        }
    }

    @ReactMethod
    fun openSettings(promise: Promise) {
        val activity = currentActivity
        if (activity == null) {
            promise.reject(E_ACTIVITY_DOES_NOT_EXIST, "Activity doesn't exist")
            return
        }

        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", activity.packageName, null)
            intent.data = uri
            activity.startActivity(intent)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("CANNOT_OPEN_SETTINGS", "Cannot open settings: ${e.message}")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ): Boolean {
        if (mPromise != null && requestCode == mRequestCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mPromise?.resolve(true)
            } else {
                mPromise?.resolve(false)
            }
            mPromise = null
            return true
        }
        return false
    }
} 