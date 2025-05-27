package com.auto_sms

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeHost
import com.facebook.react.ReactPackage
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.load
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.facebook.react.defaults.DefaultReactNativeHost
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.soloader.SoLoader
import com.auto_sms.permissions.PermissionsPackage
import com.auto_sms.callsms.CallSmsPackage
import com.auto_sms.docparser.DocParserPackage
import com.auto_sms.llm.LocalLLMPackage
import android.content.Intent
import android.os.Build
import android.util.Log

class MainApplication : Application(), ReactApplication {

  override val reactNativeHost: ReactNativeHost =
      object : DefaultReactNativeHost(this) {
        override fun getPackages(): List<ReactPackage> =
            PackageList(this).packages.apply {
              // Packages that cannot be autolinked yet can be added manually here, for example:
              // add(MyReactNativePackage())
              add(PermissionsPackage())
              add(CallSmsPackage())
              add(DocParserPackage())
              add(LocalLLMPackage())
            }

        override fun getJSMainModuleName(): String = "index"

        override fun getUseDeveloperSupport(): Boolean = BuildConfig.DEBUG

        override val isNewArchEnabled: Boolean = BuildConfig.IS_NEW_ARCHITECTURE_ENABLED
        override val isHermesEnabled: Boolean = BuildConfig.IS_HERMES_ENABLED
      }

  override val reactHost: ReactHost
    get() = getDefaultReactHost(applicationContext, reactNativeHost)

  override fun onCreate() {
    super.onCreate()
    SoLoader.init(this, OpenSourceMergedSoMapping)
    
    // Start foreground service for reliable SMS processing
    try {
      val serviceIntent = Intent(this, Class.forName("com.auto_sms.callsms.CallSmsService"))
      serviceIntent.action = "com.auto_sms.callsms.START_SERVICE"
      
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        startForegroundService(serviceIntent) 
      } else {
        startService(serviceIntent)
      }
      
      Log.d("MainApplication", "Started CallSmsService")
    } catch (e: Exception) {
      Log.e("MainApplication", "Error starting service: ${e.message}")
    }
    
    if (BuildConfig.IS_NEW_ARCHITECTURE_ENABLED) {
      // If you opted-in for the New Architecture, we load the native entry point for this app.
      load()
    }
    ReactNativeFlipper.initializeFlipper(this, reactNativeHost.reactInstanceManager)
  }
}
