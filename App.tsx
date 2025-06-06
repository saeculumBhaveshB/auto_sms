/**
 * Auto SMS App
 */

import React, { useEffect, useState, useCallback } from "react";
import {
  SafeAreaView,
  StatusBar,
  StyleSheet,
  useColorScheme,
  View,
  Text,
  TouchableOpacity,
} from "react-native";

import { PermissionsStatusScreen, LocalLLMSetupScreen } from "./src/screens";

import { CallSmsService, PermissionsService } from "./src/services";
import AutoReplyService from "./src/services/AutoReplyService";
import { NativeModules } from "react-native";
import AsyncStorage from "@react-native-async-storage/async-storage";

const { CallSmsModule, AutoReplyModule } = NativeModules;

// Define screen types
type Screen = "permissions" | "localLLM";

// Create a navigation context for tab switching
export type NavigationContextType = {
  navigateToTab: (tab: Screen) => void;
};

export const NavigationContext = React.createContext<NavigationContextType>({
  navigateToTab: () => {},
});

function App(): React.JSX.Element {
  const isDarkMode = useColorScheme() === "dark";
  const [currentScreen, setCurrentScreen] = useState<Screen>("permissions");
  const [initAttempts, setInitAttempts] = useState(0);

  const backgroundStyle = {
    backgroundColor: isDarkMode ? "#000000" : "#F5F5F5",
    flex: 1,
  };

  const navigateToTab = useCallback((tab: Screen) => {
    console.log("Navigating to tab:", tab);
    setCurrentScreen(tab);
  }, []);

  const navigationContextValue = React.useMemo(
    () => ({
      navigateToTab,
    }),
    [navigateToTab]
  );

  // Set up native SharedPreferences directly (to ensure they are set before any component uses them)
  const initializeSharedPreferences = useCallback(async () => {
    try {
      // Make direct SharedPreferences calls to ensure settings are set properly
      console.log("Initializing SharedPreferences directly...");

      // Set AI enabled
      if (CallSmsModule.setAIEnabled) {
        await CallSmsModule.setAIEnabled(true);
        console.log("AI enabled set directly in SharedPreferences");
      }

      // Set Auto SMS enabled
      if (CallSmsModule.setAutoSmsEnabled) {
        await CallSmsModule.setAutoSmsEnabled(true);
        console.log("Auto SMS enabled set directly in SharedPreferences");
      }

      // Set Auto Reply enabled - both simple and LLM using the service
      await AutoReplyService.setAutoReplyEnabled(true);
      console.log("Auto Reply enabled via AutoReplyService");

      // Enable LLM auto-reply without creating sample documents
      await AutoReplyService.setLLMAutoReplyEnabled(true);
      console.log("LLM Auto Reply enabled via AutoReplyService");

      // Also set AsyncStorage values for consistency
      await AsyncStorage.setItem("@AutoSMS:AIEnabled", "true");
      await AsyncStorage.setItem("@AutoSMS:Enabled", "true");

      return true;
    } catch (error) {
      console.error("Error initializing SharedPreferences:", error);
      return false;
    }
  }, []);

  // Initialize app settings and process pending messages on app start
  useEffect(() => {
    const initializeApp = async () => {
      try {
        console.log(`App initialization attempt #${initAttempts + 1}`);

        // First, initialize SharedPreferences directly
        const prefsInitialized = await initializeSharedPreferences();

        if (!prefsInitialized) {
          console.warn("Failed to initialize SharedPreferences directly");
        }

        // Now initialize through the service layer (which also ensures broadcast receivers are registered)
        await CallSmsService.setAIEnabled(true);
        await CallSmsService.setAutoSmsEnabled(true);

        // Auto-reply features are already initialized in initializeSharedPreferences
        // Just verify they're enabled
        const autoReplyEnabled = await AutoReplyService.isAutoReplyEnabled();
        const llmAutoReplyEnabled =
          await AutoReplyService.isLLMAutoReplyEnabled();

        console.log(
          `Auto-reply status: ${autoReplyEnabled ? "enabled" : "disabled"}`
        );
        console.log(
          `LLM auto-reply status: ${
            llmAutoReplyEnabled ? "enabled" : "disabled"
          }`
        );

        // Check permissions and start monitoring if needed
        const hasPermissions =
          await PermissionsService.areAllPermissionsGranted();
        if (hasPermissions) {
          // Start monitoring calls if not already
          const isMonitoring = CallSmsService.isMonitoringCalls();
          if (!isMonitoring) {
            await CallSmsService.startMonitoringCalls();
          }
        }

        // Process any pending messages
        const processed = await CallSmsService.processPendingMessages();
        if (processed) {
          console.log("Processed pending SMS messages");
        }

        // Mark initialization as successful
        setInitAttempts(0);
      } catch (error) {
        console.error("Error initializing app:", error);

        // If initialization failed and we haven't tried too many times, retry after a delay
        if (initAttempts < 3) {
          console.log(
            `Retrying initialization in 1 second (attempt ${
              initAttempts + 1
            }/3)`
          );
          setTimeout(() => {
            setInitAttempts((prev) => prev + 1);
          }, 1000);
        }
      }
    };

    // Run initialization
    initializeApp();

    // Set up permission check and service check interval
    const serviceCheckInterval = setInterval(async () => {
      try {
        // Check permissions
        const hasPermissions =
          await PermissionsService.areAllPermissionsGranted();

        if (hasPermissions) {
          // Make sure call monitoring is active if we have permissions
          const isMonitoring = CallSmsService.isMonitoringCalls();
          if (!isMonitoring) {
            await CallSmsService.startMonitoringCalls();
          }
        }

        // Re-verify auto-reply settings are still enabled periodically
        // This ensures they don't get lost if another component changes them
        const autoReplyEnabled = await AutoReplyService.isAutoReplyEnabled();
        const llmAutoReplyEnabled =
          await AutoReplyService.isLLMAutoReplyEnabled();

        // Re-enable auto-reply if it was disabled
        if (!autoReplyEnabled) {
          console.log("Auto Reply disabled, re-enabling...");
          await AutoReplyService.setAutoReplyEnabled(true);
        }

        // Re-enable LLM auto-reply if it was disabled - without creating sample documents
        if (!llmAutoReplyEnabled) {
          console.log("LLM Auto Reply disabled, re-enabling...");
          await AutoReplyService.setLLMAutoReplyEnabled(true);
        }
      } catch (error) {
        console.error("Error in service check interval:", error);
      }
    }, 5000);

    return () => {
      clearInterval(serviceCheckInterval);
    };
  }, [initAttempts, initializeSharedPreferences]);

  return (
    <SafeAreaView style={backgroundStyle}>
      <StatusBar
        barStyle={isDarkMode ? "light-content" : "dark-content"}
        backgroundColor={backgroundStyle.backgroundColor}
      />

      <NavigationContext.Provider value={navigationContextValue}>
        {/* Tab Navigation */}
        <View style={styles.tabContainer}>
          <TouchableOpacity
            style={[
              styles.tab,
              currentScreen === "permissions" && styles.activeTab,
            ]}
            onPress={() => navigateToTab("permissions")}
          >
            <Text
              style={[
                styles.tabText,
                currentScreen === "permissions" && styles.activeTabText,
              ]}
            >
              Permissions
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.tab,
              currentScreen === "localLLM" && styles.activeTab,
            ]}
            onPress={() => navigateToTab("localLLM")}
          >
            <Text
              style={[
                styles.tabText,
                currentScreen === "localLLM" && styles.activeTabText,
              ]}
            >
              Local LLM
            </Text>
          </TouchableOpacity>
        </View>

        {/* Screen Content */}
        {currentScreen === "permissions" ? (
          <PermissionsStatusScreen />
        ) : (
          <LocalLLMSetupScreen />
        )}
      </NavigationContext.Provider>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  tabContainer: {
    flexDirection: "row",
    backgroundColor: "#fff",
    elevation: 4,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 2,
  },
  tab: {
    flex: 1,
    paddingVertical: 12,
    alignItems: "center",
    justifyContent: "center",
    borderBottomWidth: 2,
    borderBottomColor: "transparent",
  },
  activeTab: {
    borderBottomColor: "#2196f3",
  },
  tabText: {
    fontSize: 14,
    fontWeight: "bold",
    color: "#757575",
  },
  activeTabText: {
    color: "#2196f3",
  },
  tabWithBadge: {
    alignItems: "center",
    justifyContent: "center",
  },
  activeBadge: {
    position: "absolute",
    top: -8,
    right: -15,
    backgroundColor: "#4caf50",
    borderRadius: 10,
    paddingHorizontal: 5,
    paddingVertical: 2,
  },
  badgeText: {
    color: "white",
    fontSize: 10,
    fontWeight: "bold",
  },
});

export default App;
