import React, { useState, useEffect, useCallback } from "react";
import {
  View,
  Text,
  StyleSheet,
  FlatList,
  TouchableOpacity,
  RefreshControl,
  ActivityIndicator,
  Alert,
} from "react-native";
import AIService, { ConversationHistoryItem } from "../services/AIService";

const AIChatLogScreen: React.FC = () => {
  const [conversations, setConversations] = useState<ConversationHistoryItem[]>(
    []
  );
  const [loading, setLoading] = useState<boolean>(true);
  const [refreshing, setRefreshing] = useState<boolean>(false);

  /**
   * Load conversation history
   */
  const loadConversations = useCallback(async (showLoading: boolean = true) => {
    if (showLoading) {
      setLoading(true);
    }

    try {
      const history = await AIService.getConversationHistory();
      setConversations(history);
    } catch (error) {
      console.error("Error loading conversation history:", error);
      Alert.alert("Error", "Failed to load conversation history");
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  /**
   * Handle refresh
   */
  const handleRefresh = useCallback(() => {
    setRefreshing(true);
    loadConversations(false);
  }, [loadConversations]);

  /**
   * Clear conversation history
   */
  const handleClearHistory = useCallback(() => {
    Alert.alert(
      "Clear History",
      "Are you sure you want to clear all conversation history?",
      [
        { text: "Cancel", style: "cancel" },
        {
          text: "Clear",
          style: "destructive",
          onPress: async () => {
            try {
              await AIService.clearConversationHistory();
              setConversations([]);
            } catch (error) {
              console.error("Error clearing conversation history:", error);
              Alert.alert("Error", "Failed to clear conversation history");
            }
          },
        },
      ]
    );
  }, []);

  /**
   * Format timestamp
   */
  const formatTimestamp = useCallback((timestamp: number) => {
    const date = new Date(timestamp);
    return `${date.toLocaleDateString()} | ${date.toLocaleTimeString()}`;
  }, []);

  /**
   * Format phone number
   */
  const formatPhoneNumber = useCallback((phoneNumber: string) => {
    // Simple format to improve readability
    if (phoneNumber.length === 10) {
      return `(${phoneNumber.substring(0, 3)}) ${phoneNumber.substring(
        3,
        6
      )}-${phoneNumber.substring(6)}`;
    }
    return phoneNumber;
  }, []);

  /**
   * Load conversations on mount
   */
  useEffect(() => {
    loadConversations();
  }, [loadConversations]);

  /**
   * Render conversation item
   */
  const renderItem = ({ item }: { item: ConversationHistoryItem }) => (
    <View style={styles.conversationItem}>
      <View style={styles.conversationHeader}>
        <Text style={styles.timestamp}>{formatTimestamp(item.timestamp)}</Text>
        <Text style={styles.phoneNumber}>
          {formatPhoneNumber(item.callerNumber)}
        </Text>
      </View>

      <View style={styles.messageContainer}>
        <Text style={styles.questionLabel}>❓ Caller:</Text>
        <Text style={styles.questionText}>{item.questionAsked}</Text>
      </View>

      <View style={styles.messageContainer}>
        <Text style={styles.responseLabel}>💬 AI:</Text>
        <Text style={styles.responseText}>
          {item.aiResponse.startsWith("AI:")
            ? item.aiResponse.substring(3).trim()
            : item.aiResponse}
        </Text>
      </View>
    </View>
  );

  /**
   * Render empty state
   */
  const renderEmptyComponent = () => (
    <View style={styles.emptyContainer}>
      <Text style={styles.emptyText}>No conversation history yet</Text>
      <Text style={styles.emptySubText}>
        AI responses to SMS messages will be recorded here.
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
        <Text style={styles.loadingText}>Loading conversation history...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>AI Chat Log</Text>
      </View>

      <View style={styles.listHeader}>
        <Text style={styles.listHeaderText}>Conversation History</Text>
        {conversations.length > 0 && (
          <TouchableOpacity
            onPress={handleClearHistory}
            style={styles.clearButton}
          >
            <Text style={styles.clearButtonText}>Clear</Text>
          </TouchableOpacity>
        )}
      </View>

      <FlatList
        data={conversations}
        keyExtractor={(item) => `${item.callerNumber}-${item.timestamp}`}
        renderItem={renderItem}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={renderEmptyComponent}
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={handleRefresh} />
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
  header: {
    padding: 16,
    backgroundColor: "#2196f3",
  },
  title: {
    fontSize: 20,
    fontWeight: "bold",
    color: "#fff",
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
  conversationItem: {
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
  conversationHeader: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 12,
    paddingBottom: 8,
    borderBottomWidth: 1,
    borderBottomColor: "#f0f0f0",
  },
  timestamp: {
    fontSize: 12,
    color: "#777",
  },
  phoneNumber: {
    fontSize: 14,
    fontWeight: "bold",
    color: "#333",
  },
  messageContainer: {
    marginBottom: 8,
  },
  questionLabel: {
    fontSize: 14,
    fontWeight: "bold",
    color: "#555",
    marginBottom: 4,
  },
  questionText: {
    fontSize: 16,
    color: "#333",
    marginBottom: 8,
  },
  responseLabel: {
    fontSize: 14,
    fontWeight: "bold",
    color: "#2196f3",
    marginBottom: 4,
  },
  responseText: {
    fontSize: 16,
    color: "#333",
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
});

export default AIChatLogScreen;
