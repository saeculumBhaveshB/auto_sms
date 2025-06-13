import React from "react";
import { View, Button, Text, StyleSheet, Alert } from "react-native";
import LogTester from "../utils/LogTester";

const LogTesterComponent = () => {
  // Function to generate a test SMS log entry
  const generateTestSmsLog = () => {
    LogTester.logTestSms("+1234567890", "This is a test SMS message")
      .then(() => {
        Alert.alert(
          "Success",
          "Test SMS log generated successfully. Check logcat output."
        );
      })
      .catch((error) => {
        Alert.alert(
          "Error",
          "Failed to generate test SMS log: " + error.message
        );
      });
  };

  // Function to generate a test RCS log entry
  const generateTestRcsLog = () => {
    LogTester.logTestRcs("John Doe", "This is a test RCS message")
      .then(() => {
        Alert.alert(
          "Success",
          "Test RCS log generated successfully. Check logcat output."
        );
      })
      .catch((error) => {
        Alert.alert(
          "Error",
          "Failed to generate test RCS log: " + error.message
        );
      });
  };

  // Function to simulate an incoming SMS
  const simulateIncomingSms = () => {
    LogTester.generateTestSms(
      "+1234567890",
      "This is a simulated incoming SMS message"
    )
      .then(() => {
        Alert.alert(
          "Success",
          "SMS broadcast sent successfully. Check logcat output."
        );
      })
      .catch((error) => {
        Alert.alert("Error", "Failed to simulate SMS: " + error.message);
      });
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>SMS/RCS Log Testing</Text>

      <View style={styles.buttonContainer}>
        <Button
          title="Generate Test SMS Log"
          onPress={generateTestSmsLog}
          color="#28a745"
        />
      </View>

      <View style={styles.buttonContainer}>
        <Button
          title="Generate Test RCS Log"
          onPress={generateTestRcsLog}
          color="#007bff"
        />
      </View>

      <View style={styles.buttonContainer}>
        <Button
          title="Simulate Incoming SMS"
          onPress={simulateIncomingSms}
          color="#dc3545"
        />
      </View>

      <Text style={styles.note}>
        Note: To see the logs, run './android/scripts/log_filter.sh' in a
        terminal or filter logcat with 'LOGTAG_SMS_DETAILS' or
        'LOGTAG_RCS_DETAILS'
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    padding: 20,
    backgroundColor: "#f8f9fa",
    borderRadius: 10,
    margin: 10,
  },
  title: {
    fontSize: 18,
    fontWeight: "bold",
    marginBottom: 15,
    textAlign: "center",
  },
  buttonContainer: {
    marginVertical: 8,
  },
  note: {
    marginTop: 20,
    fontSize: 12,
    color: "#6c757d",
    textAlign: "center",
  },
});

export default LogTesterComponent;
