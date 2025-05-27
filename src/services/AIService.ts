import AsyncStorage from "@react-native-async-storage/async-storage";
import RNFS from "react-native-fs";
import { OpenAI } from "openai";
import * as DocumentPicker from "@react-native-documents/picker";
import DocParserService from "./DocParserService";

// Storage keys
const AI_DOCUMENT_TEXT_KEY = "@AutoSMS:AIDocumentText";
const AI_CONVERSATION_HISTORY_KEY = "@AutoSMS:AIConversationHistory";
const AI_API_KEY_STORAGE = "@AutoSMS:OpenAIApiKey";

// OpenAI config
let openaiClient: OpenAI | null = null;

// Define conversation history interface
export interface ConversationHistoryItem {
  callerNumber: string;
  questionAsked: string;
  aiResponse: string;
  timestamp: number;
}

class AIService {
  /**
   * Initialize OpenAI client with stored API key
   */
  async initializeOpenAI(): Promise<boolean> {
    try {
      // Get API key from storage
      const apiKey = await AsyncStorage.getItem(AI_API_KEY_STORAGE);

      if (!apiKey) {
        console.log("No API key found in storage");
        return false;
      }

      openaiClient = new OpenAI({
        apiKey,
        dangerouslyAllowBrowser: true, // Since we're in a React Native environment
      });

      return true;
    } catch (error) {
      console.error("Error initializing OpenAI client:", error);
      return false;
    }
  }

  /**
   * Save OpenAI API key
   */
  async saveApiKey(apiKey: string): Promise<void> {
    await AsyncStorage.setItem(AI_API_KEY_STORAGE, apiKey);
    await this.initializeOpenAI();
  }

  /**
   * Get stored API key
   */
  async getApiKey(): Promise<string | null> {
    return await AsyncStorage.getItem(AI_API_KEY_STORAGE);
  }

  /**
   * Pick a document file (.doc)
   */
  async pickDocument(): Promise<any | null> {
    try {
      const results = await DocumentPicker.pick({
        type: [
          "application/msword",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/pdf",
        ],
      });

      return results[0];
    } catch (err) {
      if (
        DocumentPicker.isErrorWithCode(err) &&
        err.code === DocumentPicker.errorCodes.OPERATION_CANCELED
      ) {
        console.log("User cancelled document picker");
      } else {
        console.error("Error picking document:", err);
      }
      return null;
    }
  }

  /**
   * Process the document and extract text using Android native module
   */
  async processDocumentFile(uri: string): Promise<string> {
    try {
      console.log("Processing document:", uri);

      // Determine if this is a PDF file
      const isPdf = uri.toLowerCase().endsWith(".pdf") || uri.includes("pdf");

      // Use DocParserService to extract text from document
      let text = await DocParserService.parseDocument(uri);

      // For PDF files, add a note at the top about possible extraction errors
      if (isPdf && text) {
        text =
          "Note: Text has been automatically extracted from a PDF document. " +
          "Please review and correct any errors before training the AI.\n\n" +
          text;
      }

      return text;
    } catch (error: any) {
      console.error("Error processing document file:", error);

      // Special handling for PDF errors
      if (error.message) {
        if (error.message.includes("org.apache.pdfbox.pdmodel.PDDocument")) {
          throw new Error(
            "Failed to process PDF document. The file might be corrupted or password-protected."
          );
        } else if (error.message.includes("java/awt/Point")) {
          throw new Error(
            "Failed to process PDF. This PDF format is not supported."
          );
        }
      }

      throw error;
    }
  }

  /**
   * Save document text for AI training
   */
  async saveDocumentText(text: string): Promise<void> {
    try {
      await AsyncStorage.setItem(AI_DOCUMENT_TEXT_KEY, text);
    } catch (error) {
      console.error("Error saving document text:", error);
      throw error;
    }
  }

  /**
   * Retrieve saved document text
   */
  async getDocumentText(): Promise<string | null> {
    try {
      return await AsyncStorage.getItem(AI_DOCUMENT_TEXT_KEY);
    } catch (error) {
      console.error("Error retrieving document text:", error);
      return null;
    }
  }

  /**
   * Train AI with document text
   * We'll create an embedding of the document text to use for the AI's context
   */
  async trainAI(documentText: string): Promise<boolean> {
    try {
      if (!openaiClient) {
        const initialized = await this.initializeOpenAI();
        if (!initialized) return false;
      }

      // Save the document text as context for the AI
      await this.saveDocumentText(documentText);

      return true;
    } catch (error) {
      console.error("Error training AI:", error);
      return false;
    }
  }

  /**
   * Generate AI response to an SMS question
   */
  async generateResponse(
    callerNumber: string,
    question: string
  ): Promise<string> {
    try {
      if (!openaiClient) {
        const initialized = await this.initializeOpenAI();
        if (!initialized) return "AI: Sorry, I'm not available at the moment.";
      }

      // Get document text (context)
      const context = await this.getDocumentText();

      if (!context) {
        return "AI: Sorry, I haven't been trained with any information yet.";
      }

      // Generate response using OpenAI
      const completion = await openaiClient!.chat.completions.create({
        messages: [
          {
            role: "system",
            content: `You are an AI assistant responding to SMS messages. You have been trained on the following information: ${context}. If a question is asked that you cannot answer based on this information, respond with "Sorry, I am not capable of giving this answer. Wait for a call or try with a different question."`,
          },
          { role: "user", content: question },
        ],
        model: "gpt-3.5-turbo", // Can be upgraded to GPT-4 for better responses
        temperature: 0.7,
        max_tokens: 150, // Keep responses concise for SMS
      });

      const response =
        completion.choices[0]?.message?.content?.trim() ||
        "AI: Sorry, I am not capable of giving this answer. Wait for a call or try with a different question.";

      // Make sure response starts with "AI:"
      const formattedResponse = response.startsWith("AI:")
        ? response
        : `AI: ${response}`;

      // Save to conversation history
      await this.saveToConversationHistory(
        callerNumber,
        question,
        formattedResponse
      );

      return formattedResponse;
    } catch (error) {
      console.error("Error generating AI response:", error);
      return "AI: Sorry, I'm not available at the moment. Please try again later.";
    }
  }

  /**
   * Save conversation to history
   */
  async saveToConversationHistory(
    callerNumber: string,
    question: string,
    response: string
  ): Promise<void> {
    try {
      const historyJson = await AsyncStorage.getItem(
        AI_CONVERSATION_HISTORY_KEY
      );
      const history: ConversationHistoryItem[] = historyJson
        ? JSON.parse(historyJson)
        : [];

      const newItem: ConversationHistoryItem = {
        callerNumber,
        questionAsked: question,
        aiResponse: response,
        timestamp: Date.now(),
      };

      // Add new item to beginning of array
      history.unshift(newItem);

      // Save updated history
      await AsyncStorage.setItem(
        AI_CONVERSATION_HISTORY_KEY,
        JSON.stringify(history)
      );
    } catch (error) {
      console.error("Error saving conversation history:", error);
    }
  }

  /**
   * Get conversation history
   */
  async getConversationHistory(): Promise<ConversationHistoryItem[]> {
    try {
      const historyJson = await AsyncStorage.getItem(
        AI_CONVERSATION_HISTORY_KEY
      );
      return historyJson ? JSON.parse(historyJson) : [];
    } catch (error) {
      console.error("Error retrieving conversation history:", error);
      return [];
    }
  }

  /**
   * Clear conversation history
   */
  async clearConversationHistory(): Promise<void> {
    try {
      await AsyncStorage.removeItem(AI_CONVERSATION_HISTORY_KEY);
    } catch (error) {
      console.error("Error clearing conversation history:", error);
    }
  }
}

export default new AIService();
