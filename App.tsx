/**
 * Auto SMS App
 */

import React, { useEffect } from "react";
import {
  SafeAreaView,
  StatusBar,
  StyleSheet,
  useColorScheme,
} from "react-native";

import PermissionsStatusScreen from "./src/screens/PermissionsStatusScreen";

function App(): React.JSX.Element {
  const isDarkMode = useColorScheme() === "dark";

  const backgroundStyle = {
    backgroundColor: isDarkMode ? "#000000" : "#F5F5F5",
    flex: 1,
  };

  return (
    <SafeAreaView style={backgroundStyle}>
      <StatusBar
        barStyle={isDarkMode ? "light-content" : "dark-content"}
        backgroundColor={backgroundStyle.backgroundColor}
      />
      <PermissionsStatusScreen />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({});

export default App;
