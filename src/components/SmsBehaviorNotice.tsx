import React, { useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  Platform,
  TouchableOpacity,
  Alert,
} from "react-native";
import { isAndroid15OrHigher } from "../services/PermissionsService";

interface SmsBehaviorNoticeProps {
  onRequestDefault?: () => void;
  isDefaultSmsHandler?: boolean;
}

const SmsBehaviorNotice: React.FC<SmsBehaviorNoticeProps> = ({
  onRequestDefault,
  isDefaultSmsHandler = false,
}) => {
  const [isRequesting, setIsRequesting] = useState(false);

  // Only show on Android platforms
  if (Platform.OS !== "android") {
    return null;
  }

  const isAndroid15Plus = isAndroid15OrHigher();

  const handleInfoPress = () => {
    Alert.alert(
      "SMS Behavior Information",
      isAndroid15Plus
        ? "Starting with Android 15, SMS permissions are restricted to system apps and default SMS handlers only. To send SMS messages, this app must be set as your default SMS app, or it will use the system SMS app to send messages."
        : "This app can send SMS messages directly once you grant the SMS permission. No additional setup is required."
    );
  };

  const handleRequestDefault = () => {
    if (!onRequestDefault) return;

    setIsRequesting(true);

    // Show instruction alert before triggering the system dialog
    Alert.alert(
      "Set Default SMS App",
      "Please select this app in the system dialog that will appear next and tap 'Set as default'.",
      [
        {
          text: "Proceed",
          onPress: () => {
            onRequestDefault();
            // Reset the state after a delay
            setTimeout(() => setIsRequesting(false), 5000);
          },
        },
        {
          text: "Cancel",
          style: "cancel",
          onPress: () => setIsRequesting(false),
        },
      ],
      { cancelable: true }
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

      {isAndroid15Plus && !isDefaultSmsHandler && onRequestDefault && (
        <TouchableOpacity
          style={[styles.button, isRequesting && styles.buttonDisabled]}
          onPress={handleRequestDefault}
          disabled={isRequesting}
        >
          <Text style={styles.buttonText}>
            {isRequesting ? "Processing..." : "Set as Default SMS App"}
          </Text>
        </TouchableOpacity>
      )}

      {isAndroid15Plus && isDefaultSmsHandler && (
        <View style={styles.statusContainer}>
          <Text style={styles.statusText}>✓ Default SMS app</Text>
        </View>
      )}
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
});

export default SmsBehaviorNotice;
