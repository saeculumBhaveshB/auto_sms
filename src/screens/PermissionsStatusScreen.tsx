import React, { useState, useEffect, useCallback } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Alert,
  Linking,
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

  /**
   * Check all permissions
   */
  const checkAllPermissions = useCallback(async () => {
    setLoading(true);
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
    setLoading(false);
  }, []);

  /**
   * Request a specific permission
   */
  const requestPermission = useCallback(
    async (permissionType: PermissionType) => {
      try {
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
    checkAllPermissions();

    // Set interval to periodically check permissions
    const interval = setInterval(() => {
      checkAllPermissions();
    }, 3000);

    return () => clearInterval(interval);
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
  const areAllPermissionsGranted = useCallback((): boolean => {
    return REQUIRED_PERMISSIONS.every(
      (p) => permissionStatuses[p.key] === "granted"
    );
  }, [permissionStatuses]);

  if (loading) {
    return (
      <View style={styles.loadingContainer}>
        <Text>Checking permissions...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>App Permissions</Text>
      </View>

      <ScrollView style={styles.scrollView}>
        <View style={styles.infoContainer}>
          <Text style={styles.infoText}>
            This app requires the following permissions to function properly:
          </Text>
        </View>

        {areAllPermissionsGranted() ? (
          <View style={styles.allGrantedContainer}>
            <Text style={styles.allGrantedText}>
              ✅ All required permissions are granted!
            </Text>
          </View>
        ) : (
          <View style={styles.requestAllContainer}>
            <TouchableOpacity
              style={styles.requestAllButton}
              onPress={requestAllPermissions}
            >
              <Text style={styles.requestAllButtonText}>
                Grant All Permissions
              </Text>
            </TouchableOpacity>
          </View>
        )}

        {REQUIRED_PERMISSIONS.map((permission) => (
          <View key={permission.key} style={styles.permissionItem}>
            <View style={styles.permissionDetails}>
              <Text style={styles.permissionName}>{permission.name}</Text>
              <Text style={styles.permissionDescription}>
                {permission.description}
              </Text>
              <View
                style={[
                  styles.statusBadge,
                  {
                    backgroundColor: getStatusColor(
                      permissionStatuses[permission.key] || "unavailable"
                    ),
                  },
                ]}
              >
                <Text style={styles.statusText}>
                  {getStatusText(
                    permissionStatuses[permission.key] || "unavailable"
                  )}
                </Text>
              </View>
            </View>

            {permissionStatuses[permission.key] !== "granted" && (
              <TouchableOpacity
                style={styles.grantButton}
                onPress={() => requestPermission(permission.key)}
              >
                <Text style={styles.grantButtonText}>Grant</Text>
              </TouchableOpacity>
            )}
          </View>
        ))}

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
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
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
    borderRadius: 4,
    marginLeft: 8,
  },
  grantButtonText: {
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
});

export default PermissionsStatusScreen;
