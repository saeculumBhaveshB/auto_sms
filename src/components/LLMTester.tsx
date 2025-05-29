import React, { useState } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  ScrollView,
} from "react-native";
import { NativeModules } from "react-native";

const { CallSmsModule } = NativeModules;

interface LLMTesterProps {
  // No required props
}

const LLMTester: React.FC<LLMTesterProps> = () => {
  const [question, setQuestion] = useState<string>("");
  const [response, setResponse] = useState<string>("");
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const testLLM = async () => {
    if (!question.trim()) {
      setError("Please enter a question to test");
      return;
    }

    try {
      setLoading(true);
      setError(null);

      // Call the native module's testLLM function
      const result = await CallSmsModule.testLLM(question);
      setResponse(result);
    } catch (err: any) {
      console.error("Error testing LLM:", err);
      setError(`Error: ${err.message || "Unknown error occurred"}`);
      setResponse("");
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>LLM Tester</Text>
      <Text style={styles.description}>
        Test the LLM by entering a question below. The LLM will process your
        question based on your uploaded documents.
      </Text>

      <TextInput
        style={styles.input}
        placeholder="Enter your question here..."
        value={question}
        onChangeText={setQuestion}
        multiline
        numberOfLines={3}
      />

      <TouchableOpacity
        style={styles.button}
        onPress={testLLM}
        disabled={loading}
      >
        {loading ? (
          <ActivityIndicator color="#ffffff" size="small" />
        ) : (
          <Text style={styles.buttonText}>Test LLM</Text>
        )}
      </TouchableOpacity>

      {error ? <Text style={styles.errorText}>{error}</Text> : null}

      {response ? (
        <View style={styles.responseContainer}>
          <Text style={styles.responseTitle}>LLM Response:</Text>
          <ScrollView style={styles.responseScrollView}>
            <Text style={styles.responseText}>{response}</Text>
          </ScrollView>
        </View>
      ) : null}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    backgroundColor: "white",
    padding: 16,
    borderRadius: 8,
    shadowColor: "#000",
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 2,
    marginBottom: 16,
  },
  title: {
    fontSize: 18,
    fontWeight: "600",
    color: "#333",
    marginBottom: 8,
  },
  description: {
    fontSize: 14,
    color: "#666",
    marginBottom: 16,
    lineHeight: 20,
  },
  input: {
    borderWidth: 1,
    borderColor: "#ddd",
    borderRadius: 4,
    padding: 12,
    fontSize: 16,
    backgroundColor: "#f9f9f9",
    marginBottom: 16,
    textAlignVertical: "top",
  },
  button: {
    backgroundColor: "#2196f3",
    padding: 12,
    borderRadius: 4,
    alignItems: "center",
    justifyContent: "center",
    marginBottom: 16,
  },
  buttonText: {
    color: "white",
    fontWeight: "600",
    fontSize: 16,
  },
  errorText: {
    color: "#f44336",
    marginBottom: 16,
  },
  responseContainer: {
    borderTopWidth: 1,
    borderTopColor: "#eee",
    paddingTop: 16,
    marginTop: 8,
  },
  responseTitle: {
    fontSize: 16,
    fontWeight: "600",
    color: "#333",
    marginBottom: 8,
  },
  responseScrollView: {
    maxHeight: 200,
  },
  responseText: {
    fontSize: 16,
    color: "#333",
    lineHeight: 22,
  },
});

export default LLMTester;
