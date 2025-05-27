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

import {
  PermissionsStatusScreen,
  AutoSmsStatusScreen,
  AIDocumentScreen,
  AIChatLogScreen,
  LocalLLMSetupScreen,
} from "./src/screens";

import { CallSmsService } from "./src/services";

// Define screen types
type Screen =
  | "permissions"
  | "smsStatus"
  | "aiDocument"
  | "aiChatLog"
  | "localLLM";

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

  // Process pending SMS messages on app start
  useEffect(() => {
    const processPendingMessages = async () => {
      try {
        const processed = await CallSmsService.processPendingMessages();
        if (processed) {
          console.log("Processed pending SMS messages");
        }
      } catch (error) {
        console.error("Error processing pending messages:", error);
      }
    };

    processPendingMessages();
  }, []);

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
              currentScreen === "smsStatus" && styles.activeTab,
            ]}
            onPress={() => navigateToTab("smsStatus")}
          >
            <Text
              style={[
                styles.tabText,
                currentScreen === "smsStatus" && styles.activeTabText,
              ]}
            >
              Auto SMS
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[
              styles.tab,
              currentScreen === "aiDocument" && styles.activeTab,
            ]}
            onPress={() => navigateToTab("aiDocument")}
          >
            <Text
              style={[
                styles.tabText,
                currentScreen === "aiDocument" && styles.activeTabText,
              ]}
            >
              AI Setup
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

          <TouchableOpacity
            style={[
              styles.tab,
              currentScreen === "aiChatLog" && styles.activeTab,
            ]}
            onPress={() => navigateToTab("aiChatLog")}
          >
            <Text
              style={[
                styles.tabText,
                currentScreen === "aiChatLog" && styles.activeTabText,
              ]}
            >
              Chat Log
            </Text>
          </TouchableOpacity>
        </View>

        {/* Screen Content */}
        {currentScreen === "permissions" ? (
          <PermissionsStatusScreen />
        ) : currentScreen === "smsStatus" ? (
          <AutoSmsStatusScreen />
        ) : currentScreen === "aiDocument" ? (
          <AIDocumentScreen />
        ) : currentScreen === "localLLM" ? (
          <LocalLLMSetupScreen />
        ) : (
          <AIChatLogScreen />
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
});

export default App;
