package com.auto_sms.llm

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager
import java.util.ArrayList
import android.util.Log

class LocalLLMPackage : ReactPackage {
    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        val modules = ArrayList<NativeModule>()
        val llmModule = LocalLLMModule(reactContext)
        
        // Load documents on initialization
        try {
            Log.d("LocalLLMPackage", "Loading documents on startup")
            llmModule.loadDocuments()
        } catch (e: Exception) {
            Log.e("LocalLLMPackage", "Error loading documents on startup", e)
        }
        
        modules.add(llmModule)
        return modules
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return emptyList()
    }
} 