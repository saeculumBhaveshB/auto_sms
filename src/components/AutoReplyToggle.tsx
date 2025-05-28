import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  Switch,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  Button,
} from "react-native";
import { NativeModules, NativeEventEmitter } from "react-native";

// Debug check for available modules
console.log("Available native modules:", Object.keys(NativeModules));
const AutoReplyModule = NativeModules.AutoReplyModule;
console.log("AutoReplyModule available:", !!AutoReplyModule);

interface Props {
  title?: string;
}

const AutoReplyToggle: React.FC<Props> = ({ title = "Auto-Reply" }) => {
  const [isEnabled, setIsEnabled] = useState<boolean>(false);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [missedCallCount, setMissedCallCount] = useState<number>(0);
  const [debugInfo, setDebugInfo] = useState<string>("Checking module...");

  useEffect(() => {
    // Check if module is available
    if (!AutoReplyModule) {
      setError("AutoReplyModule is not available!");
      setDebugInfo("Module not found in NativeModules");
      setIsLoading(false);
      return;
    }

    // Check initial state when component mounts
    loadInitialState();

    // Load missed call numbers periodically
    const intervalId = setInterval(loadMissedCallNumbers, 60000); // every minute

    return () => {
      clearInterval(intervalId);
    };
  }, []);

  const loadInitialState = async () => {
    try {
      setIsLoading(true);
      setError(null);

      // Get the current auto-reply state
      if (
        AutoReplyModule &&
        typeof AutoReplyModule.isAutoReplyEnabled === "function"
      ) {
        const enabled = await AutoReplyModule.isAutoReplyEnabled();
        setIsEnabled(enabled);
        setDebugInfo(`Module loaded, enabled: ${enabled}`);

        // Load missed call numbers
        await loadMissedCallNumbers();
      } else {
        setError("AutoReplyModule is missing required methods");
        setDebugInfo("isAutoReplyEnabled function not found");
      }
    } catch (err: any) {
      setError("Failed to load auto-reply status");
      setDebugInfo(`Error: ${err.message || "Unknown error"}`);
      console.error("Error loading auto-reply status:", err);
    } finally {
      setIsLoading(false);
    }
  };

  const loadMissedCallNumbers = async () => {
    if (!AutoReplyModule) return;

    try {
      if (typeof AutoReplyModule.getMissedCallNumbers === "function") {
        const numbers = await AutoReplyModule.getMissedCallNumbers();
        setMissedCallCount(numbers.length);
      }
    } catch (err: any) {
      console.error("Error loading missed call numbers:", err);
    }
  };

  const toggleSwitch = async () => {
    if (!AutoReplyModule) {
      setError("AutoReplyModule is not available!");
      return;
    }

    try {
      setIsLoading(true);
      setError(null);

      const newValue = !isEnabled;
      await AutoReplyModule.setAutoReplyEnabled(newValue);
      setIsEnabled(newValue);
      setDebugInfo(`Toggle successful, new state: ${newValue}`);

      if (!newValue) {
        // Clear missed call numbers when disabling
        await AutoReplyModule.clearMissedCallNumbers();
        setMissedCallCount(0);
      }
    } catch (err: any) {
      setError("Failed to update auto-reply status");
      setDebugInfo(`Toggle error: ${err.message || "Unknown error"}`);
      console.error("Error toggling auto-reply:", err);
    } finally {
      setIsLoading(false);
    }
  };

  const clearMissedCalls = async () => {
    if (!AutoReplyModule) return;

    try {
      setIsLoading(true);
      await AutoReplyModule.clearMissedCallNumbers();
      setMissedCallCount(0);
    } catch (err: any) {
      console.error("Error clearing missed call numbers:", err);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <View style={styles.row}>
        <View style={styles.textContainer}>
          <Text style={styles.title}>{title}</Text>
          <Text style={styles.description}>
            Auto-replies "Yes I am" to incoming SMS after a missed call
          </Text>
          {error && <Text style={styles.error}>{error}</Text>}
          <Text style={styles.debugText}>{debugInfo}</Text>
        </View>

        {isLoading ? (
          <ActivityIndicator size="small" color="#0066cc" />
        ) : (
          <Switch
            trackColor={{ false: "#767577", true: "#81b0ff" }}
            thumbColor={isEnabled ? "#0066cc" : "#f4f3f4"}
            ios_backgroundColor="#3e3e3e"
            onValueChange={toggleSwitch}
            value={isEnabled}
          />
        )}
      </View>

      <Button
        title="Reload Module Status"
        onPress={loadInitialState}
        disabled={isLoading}
      />

      {isEnabled && missedCallCount > 0 && (
        <View style={styles.statusContainer}>
          <Text style={styles.statusText}>
            {missedCallCount} {missedCallCount === 1 ? "number" : "numbers"}{" "}
            ready for auto-reply
          </Text>
          <TouchableOpacity
            onPress={clearMissedCalls}
            style={styles.clearButton}
          >
            <Text style={styles.clearButtonText}>Clear</Text>
          </TouchableOpacity>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: "#f8f8f8",
    padding: 16,
    borderRadius: 8,
    marginVertical: 8,
    elevation: 2,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  row: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 8,
  },
  textContainer: {
    flex: 1,
    marginRight: 8,
  },
  title: {
    fontSize: 16,
    fontWeight: "600",
    marginBottom: 4,
  },
  description: {
    fontSize: 14,
    color: "#666",
    flexShrink: 1,
  },
  error: {
    color: "red",
    marginTop: 4,
    fontSize: 12,
  },
  debugText: {
    color: "#0066cc",
    marginTop: 4,
    fontSize: 10,
  },
  statusContainer: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginTop: 12,
    paddingTop: 12,
    borderTopWidth: 1,
    borderTopColor: "#eee",
  },
  statusText: {
    fontSize: 14,
    color: "#666",
  },
  clearButton: {
    backgroundColor: "#0066cc",
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 4,
  },
  clearButtonText: {
    color: "white",
    fontSize: 12,
    fontWeight: "600",
  },
});

export default AutoReplyToggle;
