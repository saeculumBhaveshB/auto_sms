import React, { useEffect, useState, useCallback, useContext } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  Switch,
  Alert,
  RefreshControl,
  ActivityIndicator,
} from "react-native";
import CallSmsService, { SmsHistoryItem } from "../services/CallSmsService";
import PermissionsService from "../services/PermissionsService";
import { NavigationContext, NavigationContextType } from "../../App";

const AutoSmsStatusScreen: React.FC = () => {
  const [smsHistory, setSmsHistory] = useState<SmsHistoryItem[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [isAutoSmsEnabled, setIsAutoSmsEnabled] = useState<boolean>(true);
  const [isMonitoring, setIsMonitoring] = useState<boolean>(false);
  const [permissionsGranted, setPermissionsGranted] = useState<boolean>(false);

  // Get navigation context
  const navigation = useContext(NavigationContext);

  /**
   * Check if all required permissions are granted
   */
  const checkPermissions = useCallback(async () => {
    const hasPermissions = await PermissionsService.areAllPermissionsGranted();
    setPermissionsGranted(hasPermissions);
    return hasPermissions;
  }, []);

  /**
   * Load SMS history and settings
   */
  const loadData = useCallback(
    async (showLoading: boolean = true) => {
      if (showLoading) {
        setLoading(true);
      }

      try {
        // Check permissions first
        const hasPermissions = await checkPermissions();

        // Load history
        const history = await CallSmsService.getSmsHistory();
        setSmsHistory(history);

        // Load auto SMS setting
        const autoSmsEnabled = await CallSmsService.isAutoSmsEnabled();
        setIsAutoSmsEnabled(autoSmsEnabled);

        // Check monitoring status
        const monitoring = CallSmsService.isMonitoringCalls();
        setIsMonitoring(monitoring);

        // Start monitoring if enabled, not already monitoring, and has permissions
        if (autoSmsEnabled && !monitoring && hasPermissions) {
          try {
            const started = await CallSmsService.startMonitoringCalls();
            setIsMonitoring(started);
            if (!started) {
              console.warn("Failed to start call monitoring");
            }
          } catch (err: any) {
            console.warn("Error starting call monitoring:", err.message);
            // Don't show alert here - just quietly fail as permissions may not be ready
            setIsMonitoring(false);
          }
        }
      } catch (error) {
        console.error("Error loading data:", error);
        Alert.alert("Error", "Failed to load SMS history and settings");
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [checkPermissions]
  );

  /**
   * Handle refresh
   */
  const onRefresh = useCallback(() => {
    setRefreshing(true);
    loadData(false);
  }, [loadData]);

  /**
   * Toggle auto SMS setting
   */
  const toggleAutoSms = useCallback(
    async (value: boolean) => {
      try {
        setIsAutoSmsEnabled(value);
        await CallSmsService.setAutoSmsEnabled(value);

        if (value) {
          // Check permissions before starting monitoring
          const hasPermissions = await checkPermissions();
          if (hasPermissions) {
            // Start monitoring if enabling and has permissions
            const started = await CallSmsService.startMonitoringCalls();
            setIsMonitoring(started);
          } else {
            // Show message about needing permissions
            console.warn("Cannot start monitoring: Missing permissions");
          }
        } else {
          // Stop monitoring if disabling
          await CallSmsService.stopMonitoringCalls();
          setIsMonitoring(false);
        }
      } catch (error) {
        console.error("Error toggling auto SMS:", error);
        Alert.alert("Error", "Failed to change auto SMS setting");
      }
    },
    [checkPermissions]
  );

  /**
   * Clear SMS history
   */
  const clearHistory = useCallback(() => {
    Alert.alert(
      "Clear History",
      "Are you sure you want to clear all SMS history?",
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Clear",
          style: "destructive",
          onPress: async () => {
            try {
              await CallSmsService.clearSmsHistory();
              setSmsHistory([]);
            } catch (error) {
              console.error("Error clearing history:", error);
              Alert.alert("Error", "Failed to clear SMS history");
            }
          },
        },
      ]
    );
  }, []);

  /**
   * Format phone number
   */
  const formatPhoneNumber = (phoneNumber: string) => {
    // Simple format to improve readability
    if (phoneNumber.length === 10) {
      return `(${phoneNumber.substring(0, 3)}) ${phoneNumber.substring(
        3,
        6
      )}-${phoneNumber.substring(6)}`;
    }
    return phoneNumber;
  };

  /**
   * Format timestamp
   */
  const formatTimestamp = (timestamp: number) => {
    const date = new Date(timestamp);
    return date.toLocaleString();
  };

  /**
   * Add a function to handle the permission button tap
   */
  const handleGoToPermissions = useCallback(() => {
    console.log("Going to permissions tab");
    navigation.navigateToTab("permissions");
  }, [navigation]);

  /**
   * Setup listeners and load initial data
   */
  useEffect(() => {
    // Load initial data
    loadData();

    // Set up periodic permission checks (every 2 seconds when the screen is active)
    const permissionCheckInterval = setInterval(() => {
      checkPermissions();
    }, 2000);

    // Setup event listeners
    const onSmsSent = () => loadData(false);
    const onSmsError = () => loadData(false);
    const onHistoryCleared = () => setSmsHistory([]);
    const onSettingsChanged = () => {
      CallSmsService.isAutoSmsEnabled().then((enabled) => {
        setIsAutoSmsEnabled(enabled);
      });
    };

    CallSmsService.addListener("smsSent", onSmsSent);
    CallSmsService.addListener("smsError", onSmsError);
    CallSmsService.addListener("historyCleared", onHistoryCleared);
    CallSmsService.addListener("settingsChanged", onSettingsChanged);

    // Cleanup listeners and interval
    return () => {
      clearInterval(permissionCheckInterval);
      CallSmsService.removeListener("smsSent", onSmsSent);
      CallSmsService.removeListener("smsError", onSmsError);
      CallSmsService.removeListener("historyCleared", onHistoryCleared);
      CallSmsService.removeListener("settingsChanged", onSettingsChanged);
    };
  }, [loadData, checkPermissions]);

  /**
   * Render item
   */
  const renderItem = ({ item }: { item: SmsHistoryItem }) => (
    <View style={styles.historyItem}>
      <View style={styles.historyHeader}>
        <Text style={styles.phoneNumber}>
          {formatPhoneNumber(item.phoneNumber)}
        </Text>
        <View
          style={[
            styles.statusBadge,
            { backgroundColor: item.status === "SENT" ? "#4caf50" : "#f44336" },
          ]}
        >
          <Text style={styles.statusText}>{item.status}</Text>
        </View>
      </View>

      <Text style={styles.message} numberOfLines={1} ellipsizeMode="tail">
        {item.message}
      </Text>

      <Text style={styles.timestamp}>{formatTimestamp(item.timestamp)}</Text>

      {item.error && (
        <Text style={styles.errorText} numberOfLines={1} ellipsizeMode="tail">
          Error: {item.error}
        </Text>
      )}
    </View>
  );

  /**
   * Render empty state
   */
  const renderEmptyComponent = () => (
    <View style={styles.emptyContainer}>
      <Text style={styles.emptyText}>No SMS history yet</Text>
      <Text style={styles.emptySubText}>
        When you miss a call, an SMS will be automatically sent and recorded
        here.
      </Text>
    </View>
  );

  /**
   * Render loading state
   */
  if (loading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#2196f3" />
        <Text style={styles.loadingText}>Loading SMS history...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>Auto SMS Status</Text>
      </View>

      <View style={styles.settingsContainer}>
        <View style={styles.settingRow}>
          <Text style={styles.settingText}>Auto-send SMS on missed calls</Text>
          <Switch
            value={isAutoSmsEnabled}
            onValueChange={toggleAutoSms}
            trackColor={{ false: "#767577", true: "#81b0ff" }}
            thumbColor={isAutoSmsEnabled ? "#2196f3" : "#f4f3f4"}
          />
        </View>

        <Text style={styles.monitoringText}>
          {isMonitoring
            ? "✓ Monitoring for missed calls"
            : isAutoSmsEnabled
            ? "⚠️ Not monitoring calls"
            : "● Feature disabled"}
        </Text>
      </View>

      {!permissionsGranted && isAutoSmsEnabled && (
        <View style={styles.warningContainer}>
          <Text style={styles.warningText}>
            ⚠️ Please grant all required permissions in the Permissions tab
            before using Auto SMS.
          </Text>
          <TouchableOpacity
            style={styles.permissionsButton}
            onPress={handleGoToPermissions}
          >
            <Text style={styles.permissionsButtonText}>Go to Permissions</Text>
          </TouchableOpacity>
        </View>
      )}

      <View style={styles.listHeader}>
        <Text style={styles.listHeaderText}>SMS History</Text>
        {smsHistory.length > 0 && (
          <TouchableOpacity onPress={clearHistory} style={styles.clearButton}>
            <Text style={styles.clearButtonText}>Clear</Text>
          </TouchableOpacity>
        )}
      </View>

      <FlatList
        data={smsHistory}
        keyExtractor={(item) => item.id}
        renderItem={renderItem}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={renderEmptyComponent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
        }
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f5f5f5",
  },
  loadingContainer: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "#f5f5f5",
  },
  loadingText: {
    marginTop: 12,
    fontSize: 16,
    color: "#555",
  },
  header: {
    padding: 16,
    backgroundColor: "#2196f3",
  },
  title: {
    fontSize: 20,
    fontWeight: "bold",
    color: "#fff",
  },
  settingsContainer: {
    backgroundColor: "#fff",
    padding: 16,
    marginBottom: 8,
    elevation: 2,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 1.41,
  },
  settingRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 8,
  },
  settingText: {
    fontSize: 16,
    color: "#333",
  },
  monitoringText: {
    fontSize: 14,
    color: "#555",
    marginTop: 4,
  },
  statusText: {
    color: "#fff",
    fontSize: 12,
    fontWeight: "bold",
  },
  listHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  listHeaderText: {
    fontSize: 18,
    fontWeight: "bold",
    color: "#333",
  },
  clearButton: {
    paddingVertical: 4,
    paddingHorizontal: 12,
    backgroundColor: "#ff5252",
    borderRadius: 4,
  },
  clearButtonText: {
    color: "#fff",
    fontWeight: "bold",
  },
  listContent: {
    paddingHorizontal: 16,
    paddingBottom: 16,
    flexGrow: 1,
  },
  historyItem: {
    backgroundColor: "#fff",
    padding: 16,
    borderRadius: 8,
    marginBottom: 8,
    elevation: 1,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 1.0,
  },
  historyHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 8,
  },
  phoneNumber: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#333",
  },
  statusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  message: {
    fontSize: 14,
    color: "#555",
    marginBottom: 8,
  },
  timestamp: {
    fontSize: 12,
    color: "#777",
  },
  errorText: {
    fontSize: 12,
    color: "#f44336",
    marginTop: 4,
  },
  emptyContainer: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    paddingVertical: 32,
  },
  emptyText: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#555",
    marginBottom: 8,
  },
  emptySubText: {
    fontSize: 14,
    color: "#777",
    textAlign: "center",
    paddingHorizontal: 32,
  },
  warningContainer: {
    backgroundColor: "#fff3cd",
    padding: 12,
    marginHorizontal: 16,
    marginBottom: 8,
    borderRadius: 4,
    borderLeftWidth: 4,
    borderLeftColor: "#ffeeba",
  },
  warningText: {
    fontSize: 14,
    color: "#856404",
    lineHeight: 20,
  },
  permissionsButton: {
    backgroundColor: "#ffc107",
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 4,
    marginTop: 8,
    alignSelf: "flex-start",
  },
  permissionsButtonText: {
    color: "#212529",
    fontWeight: "bold",
    fontSize: 14,
  },
});

export default AutoSmsStatusScreen;
