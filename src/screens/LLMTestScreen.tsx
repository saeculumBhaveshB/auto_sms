import React from "react";
import { View, Text, StyleSheet, ScrollView, SafeAreaView } from "react-native";
import LLMTester from "../components/LLMTester";

const LLMTestScreen: React.FC = () => {
  return (
    <SafeAreaView style={styles.container}>
      <ScrollView>
        <View style={styles.content}>
          <Text style={styles.header}>LLM Testing Tool</Text>
          <Text style={styles.subtitle}>
            Test the Local LLM functionality directly without sending SMS
          </Text>
          <LLMTester />

          <View style={styles.infoCard}>
            <Text style={styles.infoTitle}>How it works:</Text>
            <Text style={styles.infoText}>
              1. Enter your question in the text field
            </Text>
            <Text style={styles.infoText}>
              2. The app will process your question using the Local LLM
            </Text>
            <Text style={styles.infoText}>
              3. The response will be generated based on your uploaded documents
            </Text>
            <Text style={styles.infoText}>
              4. Results will display below the input field
            </Text>
          </View>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#f5f5f5",
  },
  content: {
    padding: 16,
  },
  header: {
    fontSize: 24,
    fontWeight: "bold",
    color: "#333",
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 16,
    color: "#666",
    marginBottom: 24,
  },
  infoCard: {
    backgroundColor: "#e3f2fd",
    padding: 16,
    borderRadius: 8,
    marginTop: 24,
  },
  infoTitle: {
    fontSize: 16,
    fontWeight: "600",
    color: "#1976d2",
    marginBottom: 8,
  },
  infoText: {
    fontSize: 14,
    color: "#333",
    marginBottom: 4,
    lineHeight: 20,
  },
});

export default LLMTestScreen;
