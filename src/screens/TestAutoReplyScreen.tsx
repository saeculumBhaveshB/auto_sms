import React, { useState, useEffect } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Alert,
  FlatList,
  Button,
} from "react-native";
import {
  NativeModules,
  NativeEventEmitter,
  PermissionsAndroid,
} from "react-native";
import AutoReplyToggle from "../components/AutoReplyToggle";
import PermissionsManager from "../utils/PermissionsManager";

const { AutoReplyModule } = NativeModules;

const TestAutoReplyScreen: React.FC = () => {
  const [hasRequiredPermissions, setHasRequiredPermissions] =
    useState<boolean>(false);
  const [recentMessages, setRecentMessages] = useState<any[]>([]);
  const [moduleStatus, setModuleStatus] = useState<string>("Checking...");
  const [isRequestingPermissions, setIsRequestingPermissions] =
    useState<boolean>(false);

  // Define the required permissions
  const requiredPermissions = [
    PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE,
    PermissionsAndroid.PERMISSIONS.READ_CALL_LOG,
    PermissionsAndroid.PERMISSIONS.SEND_SMS,
    PermissionsAndroid.PERMISSIONS.RECEIVE_SMS,
  ];

  useEffect(() => {
    // Debug check for AutoReplyModule
    checkAutoReplyModule();

    // Check permissions
    checkPermissions();

    // Set up event listener for SMS events
    const eventEmitter = new NativeEventEmitter(NativeModules.CallSmsModule);
    const subscription = eventEmitter.addListener(
      "onSmsReceived",
      handleSmsReceived
    );
    const sentSubscription = eventEmitter.addListener(
      "onSmsSent",
      handleSmsSent
    );

    return () => {
      subscription.remove();
      sentSubscription.remove();
    };
  }, []);

  const checkAutoReplyModule = () => {
    try {
      const moduleNames = Object.keys(NativeModules);
      console.log("Available native modules:", moduleNames);

      if (NativeModules.AutoReplyModule) {
        setModuleStatus("AutoReplyModule is available!");
        console.log("AutoReplyModule is available!");
      } else {
        setModuleStatus("AutoReplyModule NOT found!");
        console.error("AutoReplyModule is not available");
      }
    } catch (error: any) {
      setModuleStatus(`Error: ${error.message}`);
      console.error("Error checking AutoReplyModule:", error);
    }
  };

  const checkPermissions = async () => {
    try {
      const permissionsManager = new PermissionsManager();
      const results = await permissionsManager.checkMultiplePermissions(
        requiredPermissions
      );

      const allGranted = Object.values(results).every(
        (result) => result === true
      );
      setHasRequiredPermissions(allGranted);

      if (!allGranted) {
        console.log(
          "Missing permissions:",
          Object.entries(results)
            .filter(([_, granted]) => !granted)
            .map(([permission]) => permission)
        );
      }
    } catch (error) {
      console.error("Error checking permissions:", error);
    }
  };

  const requestPermissions = async () => {
    try {
      setIsRequestingPermissions(true);

      const permissionsManager = new PermissionsManager();
      const results = await permissionsManager.requestMultiplePermissions(
        requiredPermissions
      );

      const allGranted = Object.values(results).every(
        (result) => result === true
      );
      setHasRequiredPermissions(allGranted);

      if (allGranted) {
        Alert.alert("Success", "All permissions have been granted!");
      } else {
        const missing = Object.entries(results)
          .filter(([_, granted]) => !granted)
          .map(([permission]) => permission.replace("android.permission.", ""));

        Alert.alert(
          "Permissions Required",
          `The following permissions are still needed: ${missing.join(
            ", "
          )}. Without these permissions, the auto-reply feature won't work correctly.`,
          [{ text: "OK" }]
        );
      }
    } catch (error) {
      console.error("Error requesting permissions:", error);
      Alert.alert("Error", "Failed to request permissions");
    } finally {
      setIsRequestingPermissions(false);
    }
  };

  const handleSmsReceived = (event: any) => {
    setRecentMessages((prev) => [
      {
        id: `${event.phoneNumber}-${event.timestamp}`,
        type: "RECEIVED",
        ...event,
      },
      ...prev.slice(0, 19), // Keep last 20 messages
    ]);
  };

  const handleSmsSent = (event: any) => {
    setRecentMessages((prev) => [
      {
        id: `${event.phoneNumber}-${event.timestamp}`,
        type: "SENT",
        ...event,
      },
      ...prev.slice(0, 19), // Keep last 20 messages
    ]);
  };

  const renderMessageItem = ({ item }: { item: any }) => {
    const dateTime = new Date(item.timestamp);
    const formattedTime = dateTime.toLocaleTimeString();

    return (
      <View
        style={[
          styles.messageItem,
          item.type === "SENT" ? styles.sentMessage : styles.receivedMessage,
        ]}
      >
        <Text style={styles.messageHeader}>
          {item.type === "SENT" ? "To: " : "From: "}
          {item.phoneNumber}
          {item.status && ` (${item.status})`}
        </Text>
        <Text style={styles.messageContent}>{item.message}</Text>
        <Text style={styles.messageTime}>{formattedTime}</Text>
      </View>
    );
  };

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>Test Auto-Reply</Text>

      {/* Debug Section */}
      <View style={styles.debugContainer}>
        <Text style={styles.debugText}>Module Status: {moduleStatus}</Text>
        <Button title="Check Module Again" onPress={checkAutoReplyModule} />
      </View>

      {!hasRequiredPermissions && (
        <View style={styles.warningContainer}>
          <Text style={styles.warningText}>
            Some required permissions are missing. The auto-reply feature may
            not work correctly.
          </Text>
          <Button
            title="Grant Permissions"
            onPress={requestPermissions}
            disabled={isRequestingPermissions}
            color="#856404"
          />
        </View>
      )}

      <View style={styles.section}>
        <AutoReplyToggle />
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>How It Works</Text>
        <Text style={styles.instructionText}>
          1. When someone calls and doesn't reach you, the app sends them a
          message
        </Text>
        <Text style={styles.instructionText}>
          2. If they reply to that message, the app will automatically reply
          with "Yes I am"
        </Text>
        <Text style={styles.instructionText}>
          3. This only happens for people who received the missed call message
          within the last 24 hours
        </Text>
        <Text style={styles.instructionText}>
          4. The Auto Reply permission (RECEIVE_SMS) is necessary for detecting
          incoming messages and sending automatic responses
        </Text>
        <Text style={styles.noteText}>
          Note: You don't need an OpenAI API key for the basic auto-reply
          function. The "No API key found in storage" warning is only relevant
          for AI-powered responses, not for the simple "Yes I am" auto-reply.
        </Text>
      </View>

      {recentMessages.length > 0 && (
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>Recent Messages</Text>
          <FlatList
            data={recentMessages}
            renderItem={renderMessageItem}
            keyExtractor={(item) => item.id}
            scrollEnabled={false}
          />
        </View>
      )}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
    backgroundColor: "white",
  },
  title: {
    fontSize: 24,
    fontWeight: "bold",
    marginBottom: 16,
  },
  section: {
    marginBottom: 24,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: "600",
    marginBottom: 12,
  },
  warningContainer: {
    backgroundColor: "#fff3cd",
    borderColor: "#ffeeba",
    borderWidth: 1,
    borderRadius: 8,
    padding: 12,
    marginBottom: 16,
  },
  warningText: {
    color: "#856404",
  },
  instructionText: {
    fontSize: 16,
    marginBottom: 8,
  },
  messageItem: {
    padding: 12,
    borderRadius: 8,
    marginBottom: 8,
  },
  sentMessage: {
    backgroundColor: "#e1f5fe",
    alignSelf: "flex-end",
    maxWidth: "85%",
  },
  receivedMessage: {
    backgroundColor: "#f5f5f5",
    alignSelf: "flex-start",
    maxWidth: "85%",
  },
  messageHeader: {
    fontSize: 12,
    fontWeight: "600",
    marginBottom: 4,
    color: "#555",
  },
  messageContent: {
    fontSize: 15,
  },
  messageTime: {
    fontSize: 11,
    color: "#777",
    marginTop: 4,
    textAlign: "right",
  },
  debugContainer: {
    backgroundColor: "#e8f5e9",
    padding: 10,
    borderRadius: 5,
    marginBottom: 16,
  },
  debugText: {
    color: "#2e7d32",
    marginBottom: 10,
  },
  noteText: {
    fontSize: 12,
    color: "#777",
    marginTop: 8,
  },
});

export default TestAutoReplyScreen;
