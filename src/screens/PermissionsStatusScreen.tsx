import React, { useEffect, useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Alert,
  ActivityIndicator,
} from "react-native";
import PermissionsService, {
  PermissionsStatus,
  PermissionType,
} from "../services/PermissionsService";

const permissionDescriptions: Record<PermissionType, string> = {
  READ_CALL_LOG: "Required to detect missed calls and identify caller details",
  CALL_PHONE: "Required to initiate or manage phone calls",
  ANSWER_PHONE_CALLS: "Required for automatic response to incoming calls",
  READ_CONTACTS: "Required to match calls with existing contacts",
  SEND_SMS: "Required to automatically send SMS messages",
  READ_SMS: "Required to view existing SMS history",
  POST_NOTIFICATIONS: "Required to notify you about app interactions",
};

const PermissionsStatusScreen: React.FC = () => {
  const [loading, setLoading] = useState<boolean>(true);
  const [permissions, setPermissions] = useState<PermissionsStatus | null>(
    null
  );

  useEffect(() => {
    loadPermissions();
  }, []);

  const loadPermissions = async () => {
    setLoading(true);
    try {
      const status = await PermissionsService.refreshPermissionsStatus();
      setPermissions(status);
    } catch (error) {
      console.error("Error loading permissions:", error);
      Alert.alert("Error", "Failed to load permissions status");
    } finally {
      setLoading(false);
    }
  };

  const handleRequestPermission = async (permission: PermissionType) => {
    try {
      const granted = await PermissionsService.requestPermission(permission);

      if (granted) {
        // Permission granted, update the UI
        if (permissions) {
          setPermissions({
            ...permissions,
            [permission]: true,
          });
        }
      } else {
        // Permission denied, show rationale and suggest settings
        Alert.alert(
          "Permission Required",
          `${permissionDescriptions[permission]}. Please enable this permission in settings.`,
          [
            { text: "Cancel", style: "cancel" },
            {
              text: "Open Settings",
              onPress: () => PermissionsService.openSettings(),
            },
          ]
        );
      }
    } catch (error) {
      console.error(`Error requesting permission ${permission}:`, error);
      Alert.alert("Error", "Failed to request permission");
    }
  };

  if (loading) {
    return (
      <View style={styles.loadingContainer}>
        <ActivityIndicator size="large" color="#0000ff" />
        <Text style={styles.loadingText}>Loading permissions...</Text>
      </View>
    );
  }

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>Permissions Status</Text>
      <Text style={styles.subtitle}>
        The app requires these permissions to automatically send SMS messages to
        callers when you miss their calls
      </Text>

      {permissions &&
        Object.entries(permissions).map(([permission, granted]) => (
          <View key={permission} style={styles.permissionItem}>
            <View style={styles.permissionHeader}>
              <Text style={styles.permissionName}>{permission}</Text>
              <View
                style={[
                  styles.statusBadge,
                  { backgroundColor: granted ? "#4CAF50" : "#F44336" },
                ]}
              >
                <Text style={styles.statusText}>
                  {granted ? "✓ Granted" : "✗ Not Granted"}
                </Text>
              </View>
            </View>

            <Text style={styles.permissionDescription}>
              {permissionDescriptions[permission as PermissionType]}
            </Text>

            {!granted && (
              <TouchableOpacity
                style={styles.requestButton}
                onPress={() =>
                  handleRequestPermission(permission as PermissionType)
                }
              >
                <Text style={styles.requestButtonText}>Request Permission</Text>
              </TouchableOpacity>
            )}
          </View>
        ))}

      <TouchableOpacity style={styles.refreshButton} onPress={loadPermissions}>
        <Text style={styles.refreshButtonText}>Refresh Permissions</Text>
      </TouchableOpacity>

      <View style={styles.footer}>
        <Text style={styles.footerText}>
          If you denied any permission permanently, you can enable it in your
          device settings.
        </Text>
        <TouchableOpacity
          style={styles.settingsButton}
          onPress={() => PermissionsService.openSettings()}
        >
          <Text style={styles.settingsButtonText}>Open App Settings</Text>
        </TouchableOpacity>
      </View>
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
    backgroundColor: "#F5F5F5",
  },
  loadingContainer: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
  },
  loadingText: {
    marginTop: 10,
    fontSize: 16,
  },
  title: {
    fontSize: 24,
    fontWeight: "bold",
    marginBottom: 8,
    color: "#333",
  },
  subtitle: {
    fontSize: 14,
    marginBottom: 24,
    color: "#666",
  },
  permissionItem: {
    backgroundColor: "#FFFFFF",
    borderRadius: 8,
    padding: 16,
    marginBottom: 12,
    elevation: 2,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 1.41,
  },
  permissionHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 8,
  },
  permissionName: {
    fontSize: 16,
    fontWeight: "bold",
    color: "#333",
  },
  statusBadge: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
  },
  statusText: {
    color: "#FFFFFF",
    fontSize: 12,
    fontWeight: "bold",
  },
  permissionDescription: {
    fontSize: 14,
    color: "#666",
    marginBottom: 12,
  },
  requestButton: {
    backgroundColor: "#2196F3",
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 4,
    alignSelf: "flex-start",
  },
  requestButtonText: {
    color: "#FFFFFF",
    fontWeight: "bold",
  },
  refreshButton: {
    backgroundColor: "#673AB7",
    padding: 12,
    borderRadius: 4,
    alignItems: "center",
    marginVertical: 16,
  },
  refreshButtonText: {
    color: "#FFFFFF",
    fontWeight: "bold",
    fontSize: 16,
  },
  footer: {
    marginTop: 8,
    marginBottom: 24,
  },
  footerText: {
    fontSize: 14,
    color: "#666",
    marginBottom: 12,
    textAlign: "center",
  },
  settingsButton: {
    backgroundColor: "#FF9800",
    padding: 12,
    borderRadius: 4,
    alignItems: "center",
  },
  settingsButtonText: {
    color: "#FFFFFF",
    fontWeight: "bold",
    fontSize: 16,
  },
});

export default PermissionsStatusScreen;
