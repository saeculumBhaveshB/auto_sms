import React, { useState } from "react";
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  SafeAreaView,
  TouchableOpacity,
} from "react-native";
import LLMTester from "../components/LLMTester";
import Phi3MiniTester from "../components/Phi3MiniTester";

const LLMTestScreen: React.FC = () => {
  const [activeModel, setActiveModel] = useState<"default" | "phi3">("default");

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView>
        <View style={styles.content}>
          <Text style={styles.header}>LLM Testing Tool</Text>
          <Text style={styles.subtitle}>
            Test the Local LLM functionality directly without sending SMS
          </Text>

          {/* Model selection tabs */}
          <View style={styles.tabContainer}>
            <TouchableOpacity
              style={[
                styles.tab,
                activeModel === "default" && styles.activeTab,
              ]}
              onPress={() => setActiveModel("default")}
            >
              <Text
                style={[
                  styles.tabText,
                  activeModel === "default" && styles.activeTabText,
                ]}
              >
                Default LLM
              </Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.tab, activeModel === "phi3" && styles.activeTab]}
              onPress={() => setActiveModel("phi3")}
            >
              <Text
                style={[
                  styles.tabText,
                  activeModel === "phi3" && styles.activeTabText,
                ]}
              >
                Phi-3-mini
              </Text>
            </TouchableOpacity>
          </View>

          {/* Render the selected LLM tester */}
          {activeModel === "default" ? <LLMTester /> : <Phi3MiniTester />}

          <View style={styles.infoCard}>
            <Text style={styles.infoTitle}>How it works:</Text>
            <Text style={styles.infoText}>
              1. Enter your question in the text field
            </Text>
            <Text style={styles.infoText}>
              2. The app will process your question using the selected LLM
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
    marginBottom: 16,
  },
  tabContainer: {
    flexDirection: "row",
    marginBottom: 16,
    borderRadius: 8,
    overflow: "hidden",
    borderWidth: 1,
    borderColor: "#ddd",
  },
  tab: {
    flex: 1,
    padding: 12,
    alignItems: "center",
    backgroundColor: "#f5f5f5",
  },
  activeTab: {
    backgroundColor: "#2196f3",
  },
  tabText: {
    fontWeight: "600",
    color: "#666",
  },
  activeTabText: {
    color: "white",
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
