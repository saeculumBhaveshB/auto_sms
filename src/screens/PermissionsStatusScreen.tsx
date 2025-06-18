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
  AppState,
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
  const [appState, setAppState] = useState(AppState.currentState);

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

        // For notification listener, we need special handling since it opens settings
        if (permissionType === "notificationListener") {
          // Just open the settings, we'll check the status when the app comes back to foreground
          await PermissionsService.openNotificationListenerSettings();
          return;
        }

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
    [checkAllPermissions]
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
   * Handle app state changes to refresh permissions when app comes back to foreground
   */
  useEffect(() => {
    const subscription = AppState.addEventListener(
      "change",
      async (nextAppState) => {
        if (
          appState.match(/inactive|background/) &&
          nextAppState === "active"
        ) {
          console.log("App has come to the foreground, checking permissions");

          // Check permissions without showing loading indicator
          await checkAllPermissions(false);

          // After checking permissions, see if notification permissions are now granted
          // We need to get the latest status directly from the service
          const notificationListenerStatus =
            await PermissionsService.checkPermission("notificationListener");
          const notificationPermissionStatus =
            await PermissionsService.checkPermission("notificationPermission");

          // If both permissions are granted, show a notification
          if (
            notificationListenerStatus === "granted" &&
            notificationPermissionStatus === "granted"
          ) {
            // Only show if at least one of them was previously not granted
            if (
              permissionStatuses.notificationListener !== "granted" ||
              permissionStatuses.notificationPermission !== "granted"
            ) {
              // Update the UI with the new statuses
              setPermissionStatuses((prev) => ({
                ...prev,
                notificationListener: notificationListenerStatus,
                notificationPermission: notificationPermissionStatus,
              }));

              // Show notification
              Alert.alert(
                "Permissions Granted",
                "Notification permissions have been granted. RCS auto-reply is now fully functional.",
                [{ text: "OK" }]
              );
            }
          }
        }
        setAppState(nextAppState);
      }
    );

    return () => {
      subscription.remove();
    };
  }, [appState, checkAllPermissions, permissionStatuses]);

  /**
   * Initialize and check permissions
   */
  useEffect(() => {
    checkAllPermissions(true);
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

  /**
   * Check if RCS permissions are granted
   */
  const areRcsPermissionsGranted = useMemo((): boolean => {
    if (loading || Object.keys(permissionStatuses).length === 0) {
      return false;
    }

    const requiredRcsPermissions: PermissionType[] = [
      "notificationListener",
      "notificationPermission",
    ];

    return requiredRcsPermissions.every(
      (p) => permissionStatuses[p] === "granted"
    );
  }, [permissionStatuses, loading]);

  // Render each permission item
  const renderPermissionItem = useCallback(
    (permission: (typeof REQUIRED_PERMISSIONS)[number]) => {
      const status = permissionStatuses[permission.key] || "unavailable";
      const isRequesting = status === "unavailable" && refreshing;

      // Check if this is the auto reply permission
      const isAutoReply = permission.key === "autoReply";

      // Check if this is an RCS-related permission
      const isRcsPermission =
        permission.key === "notificationListener" ||
        permission.key === "notificationPermission";

      // For notification listener, we need to check the status again immediately
      // after the user presses the Grant button
      const handlePermissionRequest = async () => {
        if (permission.key === "notificationListener") {
          await requestPermission(permission.key);

          // Set a timeout to check the permission status after a short delay
          // This gives the system time to update the permission status
          setTimeout(async () => {
            const updatedStatus = await PermissionsService.checkPermission(
              permission.key
            );

            // Update the UI with the new status
            setPermissionStatuses((prev) => ({
              ...prev,
              [permission.key]: updatedStatus,
            }));

            // If permission was granted, show a notification
            if (updatedStatus === "granted") {
              // Check if notification permission is also granted
              const notificationPermissionStatus =
                permissionStatuses.notificationPermission;
              if (notificationPermissionStatus === "granted") {
                Alert.alert(
                  "Permissions Granted",
                  "All notification permissions have been granted. RCS auto-reply is now fully functional.",
                  [{ text: "OK" }]
                );
              }
            }
          }, 500);
        } else if (permission.key === "notificationPermission") {
          // Request the permission
          await requestPermission(permission.key);

          // Check the status again immediately
          setTimeout(async () => {
            const updatedStatus = await PermissionsService.checkPermission(
              permission.key
            );

            // Update the UI with the new status
            setPermissionStatuses((prev) => ({
              ...prev,
              [permission.key]: updatedStatus,
            }));

            // If permission was granted, check notification listener status
            if (updatedStatus === "granted") {
              // Check if notification listener permission is also granted
              const notificationListenerStatus =
                permissionStatuses.notificationListener;
              if (notificationListenerStatus === "granted") {
                Alert.alert(
                  "Permissions Granted",
                  "All notification permissions have been granted. RCS auto-reply is now fully functional.",
                  [{ text: "OK" }]
                );
              }
            }
          }, 500);
        } else {
          requestPermission(permission.key);
        }
      };

      return (
        <View
          key={permission.key}
          style={[
            styles.permissionItem,
            isAutoReply && styles.autoReplyPermissionItem,
            isRcsPermission && styles.rcsPermissionItem,
          ]}
        >
          <View style={styles.permissionDetails}>
            <Text
              style={[
                styles.permissionName,
                isAutoReply && styles.autoReplyText,
                isRcsPermission && styles.rcsText,
              ]}
            >
              {permission.name}
              {isAutoReply && " ✓"}
              {isRcsPermission && " 🔔"}
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
                isRcsPermission && styles.rcsButton,
              ]}
              onPress={handlePermissionRequest}
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

        {/* New RCS Notification Info Section */}
        <View style={styles.rcsInfoContainer}>
          <Text style={styles.rcsInfoTitle}>🔔 RCS Auto-Reply Permissions</Text>
          <Text style={styles.rcsInfoText}>
            For RCS message auto-replies to work properly, the app needs these
            permissions:
          </Text>
          <Text style={styles.rcsInfoItem}>
            • <Text style={styles.rcsInfoHighlight}>Notification Access</Text>:
            Allows the app to read RCS messages from your messaging apps
          </Text>
          <Text style={styles.rcsInfoItem}>
            •{" "}
            <Text style={styles.rcsInfoHighlight}>Notification Permission</Text>
            : Allows the app to send notifications about RCS auto-replies
          </Text>
          <Text style={styles.rcsInfoHighlight}>
            These permissions are essential for the AI to detect and respond to
            RCS messages.
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

            {/* Group permissions by type for better organization */}
            <View style={styles.permissionGroupHeader}>
              <Text style={styles.permissionGroupTitle}>
                SMS & Call Permissions
              </Text>
            </View>

            {REQUIRED_PERMISSIONS.filter(
              (p) =>
                !["notificationListener", "notificationPermission"].includes(
                  p.key
                )
            ).map(renderPermissionItem)}

            <View style={styles.permissionGroupHeader}>
              <Text style={styles.permissionGroupTitle}>
                RCS Auto-Reply Permissions
              </Text>
            </View>

            {REQUIRED_PERMISSIONS.filter((p) =>
              ["notificationListener", "notificationPermission"].includes(p.key)
            ).map(renderPermissionItem)}

            {!areRcsPermissionsGranted && (
              <View style={styles.rcsPermissionsWarning}>
                <Text style={styles.rcsPermissionsWarningText}>
                  ⚠️ RCS auto-reply requires notification permissions to work
                  properly.
                </Text>
              </View>
            )}

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
  // New styles for RCS permissions
  rcsPermissionItem: {
    borderLeftWidth: 3,
    borderLeftColor: "#9c27b0",
    backgroundColor: "#f3e5f5",
  },
  rcsText: {
    color: "#6a1b9a",
  },
  rcsButton: {
    backgroundColor: "#8e24aa",
  },
  rcsInfoContainer: {
    backgroundColor: "#f3e5f5",
    padding: 16,
    margin: 16,
    marginTop: 8,
    marginBottom: 8,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: "#9c27b0",
  },
  rcsInfoTitle: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#6a1b9a",
    marginBottom: 8,
  },
  rcsInfoText: {
    fontSize: 14,
    color: "#6a1b9a",
    lineHeight: 20,
    marginBottom: 8,
  },
  rcsInfoItem: {
    fontSize: 14,
    color: "#6a1b9a",
    lineHeight: 20,
    marginLeft: 8,
    marginBottom: 4,
  },
  rcsInfoHighlight: {
    fontWeight: "bold",
  },
  permissionGroupHeader: {
    backgroundColor: "#f5f5f5",
    paddingVertical: 8,
    paddingHorizontal: 16,
    marginTop: 16,
    marginBottom: 4,
  },
  permissionGroupTitle: {
    fontSize: 14,
    fontWeight: "bold",
    color: "#555",
  },
  rcsPermissionsWarning: {
    backgroundColor: "#fff3cd",
    padding: 12,
    marginHorizontal: 16,
    marginVertical: 8,
    borderRadius: 8,
    borderLeftWidth: 4,
    borderLeftColor: "#ffc107",
  },
  rcsPermissionsWarningText: {
    fontSize: 14,
    color: "#856404",
  },
});

export default PermissionsStatusScreen;
