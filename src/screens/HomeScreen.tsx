import { AutoReplyService, LocalLLMService } from '../services';

const enableLlmAutoReply = async () => {
  try {
    // First check if documents exist, if not create a sample document
    const documents = await LocalLLMService.listDocuments();
    console.log("📝 Documents count: ", documents.length);
    
    if (documents.length === 0) {
      console.log("📄 Creating sample document");
      const sampleContent = `
# Sample Document for Testing

## FAQ
Q: When will my order arrive?
A: Orders typically arrive within 3-5 business days.

Q: How do I contact support?
A: You can email support@example.com or call us at 555-123-4567.

Q: What's your refund policy?
A: We offer full refunds within 30 days of purchase.
      `;
      
      await LocalLLMService.createSampleDocument(sampleContent);
      console.log("✅ Sample document created");
    }
    
    // Then load the model if not loaded
    const isModelLoaded = await LocalLLMService.isModelLoaded();
    console.log("🔍 Is model loaded: ", isModelLoaded);
    
    if (!isModelLoaded) {
      console.log("🧠 Loading model");
      // Create a model directory and fake model file for testing
      const modelPath = `${FileSystem.documentDirectory}models/default_model.bin`;
      const modelDir = modelPath.substring(0, modelPath.lastIndexOf('/'));
      
      try {
        const dirExists = await FileSystem.exists(modelDir);
        if (!dirExists) {
          await FileSystem.makeDir(modelDir, { intermediates: true });
          console.log("📁 Created model directory");
        }
      } catch (e) {
        console.error("❌ Error creating model directory: ", e);
      }
      
      // Now load the model
      await LocalLLMService.saveSelectedModel(modelPath);
      const loadResult = await LocalLLMService.loadModel(modelPath);
      console.log("🧠 Model load result: ", loadResult);
    }
    
    // Enable LLM auto-reply
    console.log("🤖 Enabling LLM auto-reply");
    const result = await AutoReplyService.setLLMAutoReplyEnabled(true);
    
    if (result) {
      Alert.alert("Success", "LLM auto-reply enabled successfully!");
    } else {
      Alert.alert("Error", "Failed to enable LLM auto-reply");
    }
  } catch (e) {
    console.error("❌ Error enabling LLM auto-reply: ", e);
    Alert.alert("Error", "Failed to enable LLM auto-reply: " + e);
  }
};

<TouchableOpacity 
  style={styles.debugButton} 
  onPress={enableLlmAutoReply}>
  <Text style={styles.buttonText}>Enable LLM Auto-Reply</Text>
</TouchableOpacity>

debugButton: {
  backgroundColor: 'purple',
  padding: 15,
  borderRadius: 5,
  alignItems: 'center',
  marginTop: 15,
}, 