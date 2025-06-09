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
  PermissionsStatus,
  isAndroid15OrHigher,
} from "../services/PermissionsService";
import SmsBehaviorNotice from "../components/SmsBehaviorNotice";

const PermissionsStatusScreen: React.FC = () => {
  const [permissionStatuses, setPermissionStatuses] = useState<
    Record<PermissionType, PermissionStatus>
  >({} as Record<PermissionType, PermissionStatus>);
  const [loading, setLoading] = useState<boolean>(true);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [isDefaultSmsHandler, setIsDefaultSmsHandler] =
    useState<boolean>(false);

  // Check if we're on Android 15+
  const isAndroid15Plus = isAndroid15OrHigher();

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

      // Also check if we're the default SMS handler (for Android 15+)
      if (Platform.OS === "android") {
        const isSmsHandler = await PermissionsService.isDefaultSmsHandler();
        setIsDefaultSmsHandler(isSmsHandler);
      }
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

        // Special handling for SMS permissions on Android 15+
        if (
          isAndroid15Plus &&
          (permissionType === "sendSms" ||
            permissionType === "readSms" ||
            permissionType === "autoReply")
        ) {
          // On Android 15+, we need to be the default SMS handler for these permissions
          const becameDefault =
            await PermissionsService.requestDefaultSmsHandler();
          if (!becameDefault) {
            Alert.alert(
              "Default SMS App Required",
              "On Android 15+, this app must be set as the default SMS app to access SMS features. Please try again and select 'Set as Default'.",
              [{ text: "OK" }]
            );
          }

          // Refresh default SMS handler status
          const isDefault = await PermissionsService.isDefaultSmsHandler();
          setIsDefaultSmsHandler(isDefault);
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
    [isAndroid15Plus, checkAllPermissions]
  );

  /**
   * Request to become default SMS handler
   */
  const requestDefaultSmsHandler = useCallback(async () => {
    try {
      setRefreshing(true);
      const result = await PermissionsService.requestDefaultSmsHandler();

      // Refresh permissions after request
      await checkAllPermissions(false);

      if (result) {
        Alert.alert(
          "Success",
          "App is now the default SMS handler. This allows SMS functionality to work properly on all Android versions.",
          [{ text: "OK" }]
        );
      } else {
        Alert.alert(
          "Action Required",
          "Please set this app as your default SMS app when prompted. This is required for SMS functionality to work properly.",
          [
            { text: "Cancel", style: "cancel" },
            {
              text: "Try Again",
              onPress: () => requestDefaultSmsHandler(),
            },
          ]
        );
      }
    } catch (error) {
      console.error("Error requesting default SMS handler:", error);
      Alert.alert(
        "Error",
        "Failed to request default SMS handler status. Please try again.",
        [{ text: "OK" }]
      );
    } finally {
      setRefreshing(false);
    }
  }, [checkAllPermissions]);

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

    // Set up an interval to refresh status periodically
    const intervalId = setInterval(() => {
      checkAllPermissions(false);
    }, 5000); // Check every 5 seconds

    // Clean up function
    return () => clearInterval(intervalId);
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

      // Special handling for SMS-related permissions on Android 15+
      const isSmsPermission = ["sendSms", "readSms", "autoReply"].includes(
        permission.key
      );
      const isDisabled =
        isAndroid15Plus && isSmsPermission && !isDefaultSmsHandler;

      return (
        <View
          key={permission.key}
          style={[
            styles.permissionItem,
            isSmsPermission && styles.smsPermissionItem,
          ]}
        >
          <View style={styles.permissionDetails}>
            <Text
              style={[
                styles.permissionName,
                isSmsPermission && styles.smsPermissionText,
              ]}
            >
              {permission.name}
              {isAndroid15Plus &&
                isSmsPermission &&
                !isDefaultSmsHandler &&
                " 🔒"}
            </Text>
            <Text style={styles.permissionDescription}>
              {permission.description}
              {isAndroid15Plus &&
                isSmsPermission &&
                !isDefaultSmsHandler &&
                " (Requires Default SMS Handler)"}
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
                (isRequesting || isDisabled) && styles.grantButtonDisabled,
                isSmsPermission && styles.smsPermissionButton,
              ]}
              onPress={() => {
                if (
                  isAndroid15Plus &&
                  isSmsPermission &&
                  !isDefaultSmsHandler
                ) {
                  // For SMS permissions on Android 15+, request default SMS handler first
                  requestDefaultSmsHandler();
                } else {
                  requestPermission(permission.key);
                }
              }}
              disabled={isRequesting}
            >
              {isRequesting ? (
                <ActivityIndicator size="small" color="#fff" />
              ) : (
                <Text style={styles.grantButtonText}>
                  {isAndroid15Plus && isSmsPermission && !isDefaultSmsHandler
                    ? "Set Default"
                    : "Grant"}
                </Text>
              )}
            </TouchableOpacity>
          )}
        </View>
      );
    },
    [
      permissionStatuses,
      refreshing,
      requestPermission,
      isAndroid15Plus,
      isDefaultSmsHandler,
      requestDefaultSmsHandler,
    ]
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
        {/* SMS Behavior Notice for Android */}
        {Platform.OS === "android" && (
          <SmsBehaviorNotice
            onRequestDefault={requestDefaultSmsHandler}
            isDefaultSmsHandler={isDefaultSmsHandler}
          />
        )}

        {/* Default SMS Handler Button - specifically for Android 15+ */}
        {Platform.OS === "android" &&
          isAndroid15Plus &&
          !isDefaultSmsHandler && (
            <View style={styles.defaultSmsButtonContainer}>
              <Text style={styles.defaultSmsText}>
                On Android 15+, this app must be set as the default SMS app to
                access SMS features.
              </Text>
              <TouchableOpacity
                style={styles.defaultSmsButton}
                onPress={requestDefaultSmsHandler}
                disabled={refreshing}
              >
                {refreshing ? (
                  <ActivityIndicator size="small" color="#fff" />
                ) : (
                  <Text style={styles.defaultSmsButtonText}>
                    Set as Default SMS App
                  </Text>
                )}
              </TouchableOpacity>
            </View>
          )}

        <View style={styles.infoContainer}>
          <Text style={styles.infoText}>
            This app requires the following permissions to function properly:
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
  smsPermissionItem: {
    borderLeftWidth: 3,
    borderLeftColor: "#2196f3",
    backgroundColor: "#f5f9ff",
  },
  smsPermissionText: {
    color: "#0d47a1",
  },
  smsPermissionButton: {
    backgroundColor: "#1565c0",
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
  defaultSmsButtonContainer: {
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
  defaultSmsText: {
    fontSize: 14,
    color: "#333",
    marginBottom: 8,
  },
  defaultSmsButton: {
    backgroundColor: "#2196f3",
    paddingVertical: 8,
    paddingHorizontal: 16,
    borderRadius: 4,
    alignSelf: "flex-start",
  },
  defaultSmsButtonText: {
    color: "#fff",
    fontSize: 14,
    fontWeight: "bold",
  },
});

export default PermissionsStatusScreen;
