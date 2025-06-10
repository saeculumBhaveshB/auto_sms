import React, { useState, useEffect } from "react";
import {
  View,
  Text,
  StyleSheet,
  Platform,
  TouchableOpacity,
  Alert,
  ActivityIndicator,
  BackHandler,
  Linking,
  AppState,
} from "react-native";
import { isAndroid15OrHigher } from "../services/PermissionsService";
import { DeviceEventEmitter } from "react-native";

interface SmsBehaviorNoticeProps {
  onRequestDefault?: () => Promise<boolean>;
  isDefaultSmsHandler?: boolean;
}

const SmsBehaviorNotice: React.FC<SmsBehaviorNoticeProps> = ({
  onRequestDefault,
  isDefaultSmsHandler = false,
}) => {
  const [isRequesting, setIsRequesting] = useState(false);
  const [attemptCount, setAttemptCount] = useState(0);
  const [showDebugInfo, setShowDebugInfo] = useState(false);
  const [stepInstructions, setStepInstructions] = useState("");
  const [isSettingsOpened, setIsSettingsOpened] = useState(false);

  // Listen for app state changes to detect when the user returns from settings
  useEffect(() => {
    if (isSettingsOpened) {
      const appStateListener = AppState.addEventListener(
        "change",
        (nextAppState) => {
          if (nextAppState === "active") {
            // App has come back to the foreground - check status
            console.log(
              "App returned to foreground after settings, checking default SMS status"
            );
            setStepInstructions(
              "Checking if app was set as default SMS handler..."
            );

            // Wait a moment for settings to apply
            setTimeout(async () => {
              if (onRequestDefault) {
                try {
                  // This will trigger a status check
                  const result = await onRequestDefault();

                  if (result) {
                    setStepInstructions("");
                    setIsRequesting(false);
                    setIsSettingsOpened(false);

                    Alert.alert(
                      "Success!",
                      "This app has been set as your default SMS app. SMS features are now enabled.",
                      [{ text: "Great!" }]
                    );
                  } else {
                    setStepInstructions(
                      "App was not set as default SMS handler. Please try again."
                    );
                    setIsRequesting(false);
                    setIsSettingsOpened(false);
                  }
                } catch (error) {
                  console.error(
                    "Error checking default SMS status after settings:",
                    error
                  );
                  setStepInstructions("");
                  setIsRequesting(false);
                  setIsSettingsOpened(false);
                }
              }
            }, 1000);
          }
        }
      );

      return () => {
        appStateListener.remove();
      };
    }
  }, [isSettingsOpened, onRequestDefault]);

  // Listen for settings events from native code
  useEffect(() => {
    const settingsListener = DeviceEventEmitter.addListener(
      "defaultSmsHandlerSettings",
      (event) => {
        console.log("Settings event received:", event);

        if (event?.status === "settingsOpened") {
          setIsSettingsOpened(true);
          setStepInstructions(
            event.message ||
              "Please set this app as the default SMS app in Settings"
          );
        }
      }
    );

    return () => {
      settingsListener.remove();
    };
  }, []);

  // Only show on Android platforms
  if (Platform.OS !== "android") {
    return null;
  }

  const isAndroid15Plus = isAndroid15OrHigher();

  // If not Android 15+, don't show this notice
  if (!isAndroid15Plus) {
    return null;
  }

  const handleInfoPress = () => {
    Alert.alert(
      "SMS Behavior Information",
      isAndroid15Plus
        ? "Starting with Android 15, SMS permissions are restricted to system apps and default SMS handlers only. To send SMS messages, this app must be set as your default SMS app, or it will use the system SMS app to send messages."
        : "This app can send SMS messages directly once you grant the SMS permission. No additional setup is required."
    );
  };

  const handleDebugInfoPress = () => {
    setShowDebugInfo(!showDebugInfo);
  };

  const handleRequestDefault = () => {
    if (!onRequestDefault) return;

    setIsRequesting(true);
    setAttemptCount(attemptCount + 1);
    setStepInstructions("Please wait for the system dialog to appear...");

    // For Android 15+, we're going to use a more direct approach
    if (isAndroid15Plus) {
      onRequestDefault().catch((error) => {
        console.error("Error requesting default SMS handler:", error);
        setIsRequesting(false);
        setStepInstructions("");
      });
      return;
    }

    // Show detailed step-by-step instructions to guide the user
    Alert.alert(
      "Set Default SMS App",
      "To use SMS features, this app needs to be set as your default SMS app. Follow these steps:\n\n" +
        "1. Tap 'Proceed' below\n" +
        "2. In the system dialog, select this app\n" +
        "3. Tap 'Set as default' button\n" +
        "4. Wait for the process to complete\n\n" +
        "This is required for SMS functionality on Android 15+.",
      [
        {
          text: "Proceed",
          onPress: async () => {
            try {
              setStepInstructions(
                "System dialog should appear now. Select this app and tap 'Set as default'."
              );

              // Make the request and await result
              const result = await onRequestDefault();
              console.log("Default SMS handler request result:", result);

              // Clear step instructions
              setStepInstructions("");

              // If the request failed
              if (result === false) {
                // Wait a moment before showing another alert
                setTimeout(() => {
                  if (attemptCount < 3) {
                    // If we've tried less than 3 times, offer to try again
                    Alert.alert(
                      "Default SMS App Setting Failed",
                      "The app was not set as the default SMS app. This is required for SMS functionality on Android 15+.\n\n" +
                        "Common issues:\n" +
                        "• You may have pressed Back or Home during the process\n" +
                        "• You may have selected a different app\n" +
                        "• The system dialog may not have appeared",
                      [
                        {
                          text: "Try Again",
                          onPress: () => handleRequestDefault(),
                        },
                        {
                          text: "Open Android Settings",
                          onPress: () => {
                            Linking.openSettings();
                          },
                        },
                        {
                          text: "Cancel",
                          style: "cancel",
                          onPress: () => setIsRequesting(false),
                        },
                      ]
                    );
                  } else {
                    // If we've tried 3 or more times, show guidance for manual setting
                    Alert.alert(
                      "Manual Default SMS App Setting",
                      "To set this app as the default SMS app manually:\n\n" +
                        "1. Go to your Android Settings\n" +
                        "2. Search for 'Default apps'\n" +
                        "3. Tap on 'SMS app'\n" +
                        "4. Select this app from the list\n\n" +
                        "Would you like to open Android Settings now?",
                      [
                        {
                          text: "Open Settings",
                          onPress: () => Linking.openSettings(),
                        },
                        {
                          text: "Cancel",
                          style: "cancel",
                          onPress: () => setIsRequesting(false),
                        },
                      ]
                    );
                  }
                }, 1000);
              } else {
                // Success - reset requesting state
                Alert.alert(
                  "Success!",
                  "This app has been set as your default SMS app. SMS features are now enabled.",
                  [{ text: "OK" }]
                );
                setIsRequesting(false);
              }
            } catch (error) {
              console.error("Error in handleRequestDefault:", error);
              setIsRequesting(false);
              setStepInstructions("");

              // Show error alert
              Alert.alert(
                "Error",
                "There was an error setting the app as the default SMS app. Please try again or set it manually in Settings > Apps > Default apps > SMS app.",
                [{ text: "OK" }]
              );
            }
          },
        },
        {
          text: "Cancel",
          style: "cancel",
          onPress: () => {
            setIsRequesting(false);
            setStepInstructions("");
          },
        },
      ]
    );
  };

  // Special UI when there have been multiple attempts
  const renderMultipleAttemptsUI = () => {
    if (attemptCount < 2) return null;

    return (
      <View style={styles.troubleshootContainer}>
        <Text style={styles.troubleshootText}>
          Having trouble? Make sure to:
        </Text>
        <Text style={styles.troubleshootItem}>
          • Tap "Set as default" in the system dialog
        </Text>
        <Text style={styles.troubleshootItem}>
          • Complete the entire system process
        </Text>
        <Text style={styles.troubleshootItem}>
          • Don't press back or home during the process
        </Text>

        <TouchableOpacity
          style={styles.settingsButton}
          onPress={() => Linking.openSettings()}
        >
          <Text style={styles.settingsButtonText}>Open Android Settings</Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.debugButton}
          onPress={handleDebugInfoPress}
        >
          <Text style={styles.debugButtonText}>
            {showDebugInfo ? "Hide Technical Info" : "Show Technical Info"}
          </Text>
        </TouchableOpacity>

        {showDebugInfo && (
          <View style={styles.debugInfo}>
            <Text style={styles.debugText}>
              • Android Version: {Platform.Version}
            </Text>
            <Text style={styles.debugText}>
              • isAndroid15Plus: {isAndroid15Plus ? "Yes" : "No"}
            </Text>
            <Text style={styles.debugText}>
              • isDefaultSmsHandler: {isDefaultSmsHandler ? "Yes" : "No"}
            </Text>
            <Text style={styles.debugText}>
              • Attempt Count: {attemptCount}
            </Text>
            <Text style={styles.debugText}>
              • Settings Opened: {isSettingsOpened ? "Yes" : "No"}
            </Text>
          </View>
        )}
      </View>
    );
  };

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>
          {isAndroid15Plus ? "Android 15+ SMS Notice" : "SMS Behavior Notice"}
        </Text>
        <TouchableOpacity onPress={handleInfoPress} style={styles.infoButton}>
          <Text style={styles.infoButtonText}>ⓘ</Text>
        </TouchableOpacity>
      </View>

      <Text style={styles.description}>
        {isAndroid15Plus
          ? isDefaultSmsHandler
            ? "This app is currently set as your default SMS app, allowing it to send messages directly."
            : "On Android 15+, to send SMS messages directly, this app must be set as your default SMS app."
          : "This app requires SMS permission to send automatic replies to missed calls."}
      </Text>

      {stepInstructions ? (
        <View style={styles.instructionsContainer}>
          <Text style={styles.instructionsText}>{stepInstructions}</Text>
        </View>
      ) : null}

      {isAndroid15Plus && !isDefaultSmsHandler && onRequestDefault && (
        <TouchableOpacity
          style={[styles.button, isRequesting && styles.buttonDisabled]}
          onPress={handleRequestDefault}
          disabled={isRequesting}
        >
          {isRequesting ? (
            <View style={styles.processingContainer}>
              <ActivityIndicator size="small" color="#fff" />
              <Text style={styles.buttonText}>Processing...</Text>
            </View>
          ) : (
            <Text style={styles.buttonText}>Set as Default SMS App</Text>
          )}
        </TouchableOpacity>
      )}

      {isAndroid15Plus && isDefaultSmsHandler && (
        <View style={styles.statusContainer}>
          <Text style={styles.statusText}>✓ Default SMS app</Text>
        </View>
      )}

      {renderMultipleAttemptsUI()}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    margin: 16,
    padding: 16,
    backgroundColor: "#e3f2fd",
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: "#2196f3",
    elevation: 2,
    shadowColor: "#000",
    shadowOffset: {
      width: 0,
      height: 1,
    },
    shadowOpacity: 0.2,
    shadowRadius: 1.41,
  },
  header: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 8,
  },
  title: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#0d47a1",
  },
  infoButton: {
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: "#2196f3",
    justifyContent: "center",
    alignItems: "center",
  },
  infoButtonText: {
    color: "#fff",
    fontSize: 14,
    fontWeight: "bold",
  },
  description: {
    fontSize: 14,
    color: "#333",
    marginBottom: 16,
    lineHeight: 20,
  },
  instructionsContainer: {
    backgroundColor: "#fff",
    padding: 12,
    borderRadius: 4,
    marginBottom: 16,
    borderLeftWidth: 3,
    borderLeftColor: "#ffc107",
  },
  instructionsText: {
    fontSize: 14,
    color: "#333",
    fontWeight: "500",
  },
  button: {
    backgroundColor: "#2196f3",
    padding: 12,
    borderRadius: 4,
    alignItems: "center",
  },
  buttonDisabled: {
    backgroundColor: "#90caf9",
  },
  buttonText: {
    color: "#fff",
    fontWeight: "bold",
    fontSize: 14,
  },
  processingContainer: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "center",
  },
  statusContainer: {
    backgroundColor: "#e8f5e9",
    padding: 8,
    borderRadius: 4,
    alignItems: "center",
  },
  statusText: {
    color: "#2e7d32",
    fontWeight: "bold",
  },
  troubleshootContainer: {
    marginTop: 16,
    padding: 10,
    backgroundColor: "#fff3cd",
    borderRadius: 4,
    borderLeftWidth: 3,
    borderLeftColor: "#ffc107",
  },
  troubleshootText: {
    fontSize: 14,
    fontWeight: "bold",
    color: "#856404",
    marginBottom: 6,
  },
  troubleshootItem: {
    fontSize: 13,
    color: "#856404",
    marginBottom: 4,
    marginLeft: 4,
  },
  settingsButton: {
    marginTop: 10,
    backgroundColor: "#ffc107",
    padding: 8,
    borderRadius: 4,
    alignItems: "center",
    marginBottom: 10,
  },
  settingsButtonText: {
    fontSize: 14,
    color: "#333",
    fontWeight: "bold",
  },
  debugButton: {
    backgroundColor: "#f8f9fa",
    padding: 6,
    borderRadius: 4,
    alignSelf: "flex-start",
  },
  debugButtonText: {
    fontSize: 12,
    color: "#6c757d",
  },
  debugInfo: {
    marginTop: 8,
    padding: 8,
    backgroundColor: "#f8f9fa",
    borderRadius: 4,
  },
  debugText: {
    fontSize: 12,
    fontFamily: "monospace",
    color: "#6c757d",
    marginBottom: 2,
  },
});

export default SmsBehaviorNotice;
