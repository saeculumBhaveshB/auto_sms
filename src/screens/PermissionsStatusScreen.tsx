import React, { useState, useEffect, useCallback, useMemo } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Alert,
  Linking,
  ActivityIndicator,
  Platform,
} from "react-native";
import PermissionsService, {
  REQUIRED_PERMISSIONS,
  PermissionType,
  PermissionStatus,
} from "../services/PermissionsService";

const PermissionsStatusScreen: React.FC = () => {
  const [permissionStatuses, setPermissionStatuses] = useState<
    Record<PermissionType, PermissionStatus>
  >({} as Record<PermissionType, PermissionStatus>);

  const [loading, setLoading] = useState<boolean>(true);
  const [refreshing, setRefreshing] = useState<boolean>(false);

  /**
   * Check all permissions
   */
  const checkAllPermissions = useCallback(async (showLoading = true) => {
    if (showLoading) {
      setRefreshing(true);
    }

    try {
      const statuses: Record<PermissionType, PermissionStatus> = {} as Record<
        PermissionType,
        PermissionStatus
      >;

      for (const permission of REQUIRED_PERMISSIONS) {
        statuses[permission.key] = await PermissionsService.checkPermission(
          permission.key
        );
      }

      setPermissionStatuses(statuses);
    } catch (error) {
      console.error("Error checking permissions:", error);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  /**
   * Request a specific permission
   */
  const requestPermission = useCallback(
    async (permissionType: PermissionType) => {
      try {
        // Update UI to show requesting state
        setPermissionStatuses((prev) => ({
          ...prev,
          [permissionType]: "unavailable", // Temporary status while requesting
        }));

        const status = await PermissionsService.requestPermission(
          permissionType
        );

        // Update state with new status
        setPermissionStatuses((prev) => ({
          ...prev,
          [permissionType]: status,
        }));

        if (status === "blocked" || status === "denied") {
          Alert.alert(
            "Permission Required",
            `This app needs ${
              REQUIRED_PERMISSIONS.find((p) => p.key === permissionType)?.name
            } permission to function properly. Please enable it in your device settings.`,
            [
              { text: "Cancel", style: "cancel" },
              {
                text: "Open Settings",
                onPress: () => Linking.openSettings(),
              },
            ]
          );
        }
      } catch (error) {
        console.error("Error requesting permission:", error);
        Alert.alert("Error", "Failed to request permission");

        // Reset status on error
        checkAllPermissions(false);
      }
    },
    []
  );

  /**
   * Request all permissions at once
   */
  const requestAllPermissions = useCallback(async () => {
    for (const permission of REQUIRED_PERMISSIONS) {
      await requestPermission(permission.key);
    }
  }, [requestPermission]);

  /**
   * Initialize and check permissions
   */
  useEffect(() => {
    checkAllPermissions(true);

    // Run a single permission check when screen is focused
    // instead of constantly checking with an interval
    const focusListener = () => {
      checkAllPermissions(false);
    };

    // Clean up function
    return () => {};
  }, [checkAllPermissions]);

  /**
   * Get badge color for permission status
   */
  const getStatusColor = (status: PermissionStatus): string => {
    switch (status) {
      case "granted":
        return "#4caf50"; // Green
      case "denied":
        return "#ff9800"; // Orange
      case "blocked":
        return "#f44336"; // Red
      default:
        return "#9e9e9e"; // Gray
    }
  };

  /**
   * Get user-friendly status text
   */
  const getStatusText = (status: PermissionStatus): string => {
    switch (status) {
      case "granted":
        return "Granted";
      case "denied":
        return "Denied";
      case "blocked":
        return "Blocked";
      case "unavailable":
        return "Unavailable";
      case "limited":
        return "Limited";
      case "never_ask_again":
        return "Never Ask Again";
      default:
        return "Unknown";
    }
  };

  /**
   * Check if all required permissions are granted
   */
  const areAllPermissionsGranted = useMemo((): boolean => {
    if (loading || Object.keys(permissionStatuses).length === 0) {
      return false;
    }
    return REQUIRED_PERMISSIONS.every(
      (p) => permissionStatuses[p.key] === "granted"
    );
  }, [permissionStatuses, loading]);

  // Render each permission item
  const renderPermissionItem = useCallback(
    (permission: (typeof REQUIRED_PERMISSIONS)[number]) => {
      const status = permissionStatuses[permission.key] || "unavailable";
      const isRequesting = status === "unavailable" && refreshing;
      // Check if this is the auto reply permission
      const isAutoReply = permission.key === "autoReply";
      // Check if this is the notifications permission
      const isNotifications = permission.key === "notifications";

      return (
        <View
          key={permission.key}
          style={[
            styles.permissionItem,
            isAutoReply && styles.autoReplyPermissionItem,
            isNotifications && styles.notificationsPermissionItem,
          ]}
        >
          <View style={styles.permissionDetails}>
            <Text
              style={[
                styles.permissionName,
                isAutoReply && styles.autoReplyText,
                isNotifications && styles.notificationsText,
              ]}
            >
              {permission.name}
              {isAutoReply && " ✓"}
              {isNotifications && " 🔔"}
            </Text>
            <Text style={styles.permissionDescription}>
              {permission.description}
            </Text>
            <View
              style={[
                styles.statusBadge,
                {
                  backgroundColor: getStatusColor(status),
                },
              ]}
            >
              <Text style={styles.statusText}>{getStatusText(status)}</Text>
            </View>
          </View>

          {status !== "granted" && (
            <TouchableOpacity
              style={[
                styles.grantButton,
                isRequesting && styles.grantButtonDisabled,
                isAutoReply && styles.autoReplyButton,
                isNotifications && styles.notificationsButton,
              ]}
              onPress={() => requestPermission(permission.key)}
              disabled={isRequesting}
            >
              {isRequesting ? (
                <ActivityIndicator size="small" color="#fff" />
              ) : (
                <Text style={styles.grantButtonText}>Grant</Text>
              )}
            </TouchableOpacity>
          )}
        </View>
      );
    },
    [permissionStatuses, refreshing, requestPermission]
  );

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>App Permissions</Text>
        {refreshing && !loading && (
          <ActivityIndicator
            size="small"
            color="#fff"
            style={styles.headerLoader}
          />
        )}
      </View>

      <ScrollView style={styles.scrollView}>
        <View style={styles.infoContainer}>
          <Text style={styles.infoText}>
            This app requires the following permissions to function properly:
          </Text>
        </View>

        {/* Auto Reply Info Section */}
        <View style={styles.autoReplyInfoContainer}>
          <Text style={styles.autoReplyInfoTitle}>
            🔔 Auto Reply Permission (RECEIVE_SMS)
          </Text>
          <Text style={styles.autoReplyInfoText}>
            The Auto Reply feature requires the RECEIVE_SMS permission to
            automatically respond to incoming messages. This is crucial for the
            AI to detect when someone replies to your missed call message and
            provide intelligent responses.
          </Text>
          <Text style={styles.autoReplyInfoHighlight}>
            AI responses are always enabled to provide the best user experience.
          </Text>
        </View>

        {/* Notifications Info Section */}
        <View style={styles.notificationsInfoContainer}>
          <Text style={styles.notificationsInfoTitle}>
            📱 Notifications Permission
          </Text>
          <Text style={styles.notificationsInfoText}>
            The app needs notification permission to alert you about important
            events such as missed calls, incoming messages, and auto-reply
            status. This ensures you stay informed about the app's activities
            even when it's running in the background.
          </Text>
          <Text style={styles.notificationsInfoHighlight}>
            Notifications are essential for keeping you updated on auto-reply
            activities.
          </Text>
        </View>

        {loading ? (
          <View style={styles.loadingContainer}>
            <ActivityIndicator size="large" color="#2196f3" />
            <Text style={{ marginTop: 10 }}>Checking permissions...</Text>
          </View>
        ) : (
          <>
            {areAllPermissionsGranted ? (
              <View style={styles.allGrantedContainer}>
                <Text style={styles.allGrantedText}>
                  ✅ All required permissions are granted!
                </Text>
                <Text style={styles.allGrantedSubtext}>
                  AI responses are active and ready to help your contacts when
                  you're busy.
                </Text>
              </View>
            ) : (
              <View style={styles.requestAllContainer}>
                <Text style={styles.requestAllText}>
                  Please grant all permissions for the best experience with AI
                  responses.
                </Text>
                <TouchableOpacity
                  style={styles.requestAllButton}
                  onPress={requestAllPermissions}
                  disabled={refreshing}
                >
                  {refreshing ? (
                    <ActivityIndicator size="small" color="#fff" />
                  ) : (
                    <Text style={styles.requestAllButtonText}>
                      Grant All Permissions
                    </Text>
                  )}
                </TouchableOpacity>
              </View>
            )}

            {REQUIRED_PERMISSIONS.map(renderPermissionItem)}

            <TouchableOpacity
              style={styles.refreshButton}
              onPress={() => checkAllPermissions(true)}
              disabled={refreshing}
            >
              {refreshing ? (
                <ActivityIndicator size="small" color="#fff" />
              ) : (
                <Text style={styles.refreshButtonText}>Refresh Status</Text>
              )}
            </TouchableOpacity>

            <View style={styles.noteContainer}>
              <Text style={styles.noteText}>
                Note: If permissions are blocked, you may need to enable them
                manually in your device settings.
              </Text>
              <TouchableOpacity
                style={styles.settingsButton}
                onPress={() => Linking.openSettings()}
              >
                <Text style={styles.settingsButtonText}>Open Settings</Text>
              </TouchableOpacity>
            </View>
          </>
        )}
      </ScrollView>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f5f5f5",
  },
  loadingContainer: {
    padding: 40,
    justifyContent: "center",
    alignItems: "center",
  },
  header: {
    padding: 16,
    backgroundColor: "#2196f3",
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
  },
  headerLoader: {
    marginRight: 10,
  },
  title: {
    fontSize: 20,
    fontWeight: "bold",
    color: "#fff",
  },
  scrollView: {
    flex: 1,
  },
  infoContainer: {
    backgroundColor: "#fff",
    padding: 16,
    margin: 16,
    marginBottom: 8,
    borderRadius: 8,
    elevation: 2,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 1.41,
  },
  infoText: {
    fontSize: 16,
    color: "#333",
  },
  allGrantedContainer: {
    backgroundColor: "#e8f5e9",
    padding: 16,
    margin: 16,
    marginTop: 8,
    marginBottom: 8,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: "#4caf50",
  },
  allGrantedText: {
    fontSize: 16,
    color: "#2e7d32",
  },
  requestAllContainer: {
    padding: 16,
    alignItems: "center",
  },
  requestAllButton: {
    backgroundColor: "#2196f3",
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 4,
    minWidth: 200,
    alignItems: "center",
    elevation: 2,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 1.41,
  },
  requestAllButtonText: {
    color: "#fff",
    fontSize: 16,
    fontWeight: "bold",
  },
  permissionItem: {
    flexDirection: "row",
    backgroundColor: "#fff",
    padding: 16,
    marginHorizontal: 16,
    marginVertical: 4,
    borderRadius: 8,
    elevation: 1,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 1.0,
    justifyContent: "space-between",
    alignItems: "center",
  },
  permissionDetails: {
    flex: 1,
  },
  permissionName: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#333",
    marginBottom: 4,
  },
  permissionDescription: {
    fontSize: 14,
    color: "#666",
    marginBottom: 8,
  },
  statusBadge: {
    alignSelf: "flex-start",
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  statusText: {
    color: "#fff",
    fontWeight: "bold",
    fontSize: 12,
  },
  grantButton: {
    backgroundColor: "#2196f3",
    paddingVertical: 8,
    paddingHorizontal: 16,
    minWidth: 80,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 4,
    marginLeft: 8,
  },
  grantButtonDisabled: {
    backgroundColor: "#90caf9",
  },
  grantButtonText: {
    color: "#fff",
    fontSize: 14,
    fontWeight: "bold",
  },
  refreshButton: {
    backgroundColor: "#673ab7",
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 4,
    margin: 16,
    alignSelf: "center",
    alignItems: "center",
    minWidth: 150,
  },
  refreshButtonText: {
    color: "#fff",
    fontSize: 14,
    fontWeight: "bold",
  },
  noteContainer: {
    backgroundColor: "#fff3cd",
    padding: 16,
    margin: 16,
    marginTop: 8,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: "#ffeeba",
  },
  noteText: {
    fontSize: 14,
    color: "#856404",
    marginBottom: 8,
  },
  settingsButton: {
    backgroundColor: "#ffc107",
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 4,
    alignSelf: "flex-start",
  },
  settingsButtonText: {
    color: "#212529",
    fontWeight: "bold",
    fontSize: 14,
  },
  autoReplyPermissionItem: {
    borderLeftWidth: 3,
    borderLeftColor: "#2196f3",
    backgroundColor: "#f5f9ff",
  },
  autoReplyText: {
    color: "#0d47a1",
  },
  autoReplyButton: {
    backgroundColor: "#1565c0",
  },
  autoReplyInfoContainer: {
    backgroundColor: "#e3f2fd",
    padding: 16,
    margin: 16,
    marginTop: 8,
    marginBottom: 8,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: "#2196f3",
  },
  autoReplyInfoTitle: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#0d47a1",
    marginBottom: 8,
  },
  autoReplyInfoText: {
    fontSize: 14,
    color: "#1565c0",
    lineHeight: 20,
  },
  autoReplyInfoHighlight: {
    fontSize: 14,
    color: "#1565c0",
    lineHeight: 20,
    fontWeight: "bold",
    marginTop: 8,
  },
  notificationsPermissionItem: {
    borderLeftWidth: 3,
    borderLeftColor: "#9c27b0",
    backgroundColor: "#f3e5f5",
  },
  notificationsText: {
    color: "#6a1b9a",
  },
  notificationsButton: {
    backgroundColor: "#8e24aa",
  },
  notificationsInfoContainer: {
    backgroundColor: "#f3e5f5",
    padding: 16,
    margin: 16,
    marginTop: 8,
    marginBottom: 8,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: "#9c27b0",
  },
  notificationsInfoTitle: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#6a1b9a",
    marginBottom: 8,
  },
  notificationsInfoText: {
    fontSize: 14,
    color: "#8e24aa",
    lineHeight: 20,
  },
  notificationsInfoHighlight: {
    fontSize: 14,
    color: "#8e24aa",
    lineHeight: 20,
    fontWeight: "bold",
    marginTop: 8,
  },
  allGrantedSubtext: {
    fontSize: 14,
    color: "#2e7d32",
    marginTop: 8,
  },
  requestAllText: {
    fontSize: 16,
    color: "#333",
    marginBottom: 12,
    textAlign: "center",
  },
});

export default PermissionsStatusScreen;
