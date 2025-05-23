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

import PermissionsStatusScreen from "./src/screens/PermissionsStatusScreen";
import AutoSmsStatusScreen from "./src/screens/AutoSmsStatusScreen";

// Create a context to share tab navigation functions
export const NavigationContext = React.createContext<{
  navigateToTab: (tab: Screen) => void;
}>({
  navigateToTab: () => {},
});

type Screen = "permissions" | "smsStatus";

function App(): React.JSX.Element {
  const isDarkMode = useColorScheme() === "dark";
  const [currentScreen, setCurrentScreen] = useState<Screen>("permissions");

  const backgroundStyle = {
    backgroundColor: isDarkMode ? "#000000" : "#F5F5F5",
    flex: 1,
  };

  const navigateToTab = useCallback((tab: Screen) => {
    setCurrentScreen(tab);
  }, []);

  const navigationContextValue = {
    navigateToTab,
  };

  return (
    <SafeAreaView style={backgroundStyle}>
      <StatusBar
        barStyle={isDarkMode ? "light-content" : "dark-content"}
        backgroundColor={backgroundStyle.backgroundColor}
      />

      {/* Tab Navigation */}
      <View style={styles.tabContainer}>
        <TouchableOpacity
          style={[
            styles.tab,
            currentScreen === "permissions" && styles.activeTab,
          ]}
          onPress={() => setCurrentScreen("permissions")}
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
          onPress={() => setCurrentScreen("smsStatus")}
        >
          <Text
            style={[
              styles.tabText,
              currentScreen === "smsStatus" && styles.activeTabText,
            ]}
          >
            Auto SMS Status
          </Text>
        </TouchableOpacity>
      </View>

      {/* Screen Content */}
      <NavigationContext.Provider value={navigationContextValue}>
        {currentScreen === "permissions" ? (
          <PermissionsStatusScreen />
        ) : (
          <AutoSmsStatusScreen />
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
