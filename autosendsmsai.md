📌 Overview
This phase focuses on implementing a robust AI-powered SMS auto-response system for missed calls. The app allows professionals (Lawyers, Doctors, Police Inspectors, etc.) to upload a document (in .doc format). Upon upload, the text is displayed in an editable text input for review or modification. After the user confirms the text, it is utilized to train/configure an AI model. The trained AI then provides automated responses via SMS when the user receives missed calls and the caller replies back through SMS queries.

🚀 Detailed Functional Requirements

1. Document Upload and Parsing
   Allow users to upload .doc files from their device storage.

Upon successful upload, parse the document and extract all text content accurately.

2. Text Review and Editing
   Display extracted document text clearly in a large, scrollable text input field.

Allow users to review and edit this text freely before submission for AI training.

3. AI Model Training
   Provide a button labeled "Read AI".

Upon clicking "Read AI", the AI model uses the edited/confirmed text for:

Internal training/configuration.

Gaining context to answer queries confidently and accurately.

4. SMS Interaction Flow
   When a call is missed, initiate SMS-based interaction as follows:

Initial Auto-Reply SMS:

mathematica
Copy
Edit
AI: I am busy, available only for chat. How may I help you?
AI-Driven Replies:

When the caller sends a question via SMS, the trained AI must auto-reply directly based on the trained context.

All replies start explicitly with "AI:".

If the AI identifies the question is unrelated or outside of its trained context, it should reply with:

vbnet
Copy
Edit
AI: Sorry, I am not capable of giving this answer. Wait for a call or try with a different question.
📱 Technical Requirements

1. Document Handling (React Native)
   Use a reliable React Native library (react-native-document-picker) to pick .doc files.

Implement native modules (Android) for parsing .doc files accurately into readable plain text.

2. Editable Text Input Interface
   Provide a clear and intuitive UI screen:

Title: "Document Text Editor"

Components:

Large editable TextInput.

A "Read AI" button.

3. AI Integration
   Integrate AI via an API (OpenAI or other robust LLM service).

When the "Read AI" button is pressed, upload the reviewed text to the AI API to generate a context-aware embedding.

Store generated embeddings securely for efficient query responses (locally or via cloud-based embedding retrieval).

4. Auto SMS Response Logic
   Implement auto-detection of SMS messages from the caller post missed call.

Forward the incoming SMS question to the AI API with the trained embeddings/context.

Receive AI-generated responses, prefix them explicitly with "AI:", and auto-send SMS responses immediately.

Use the native Android SmsManager API to send SMS responses directly.

🗃️ Data Storage & Management

1. Local Storage (AsyncStorage)
   Store the uploaded and reviewed document text.

Store AI-trained embeddings or a unique ID referencing trained context.

Maintain logs of SMS interactions clearly:

json
Copy
Edit
{
"callerNumber": "+91XXXXXXXXXX",
"questionAsked": "Caller question here",
"aiResponse": "AI: your answer here",
"timestamp": "2025-05-23T12:30:00Z"
} 2. Interaction Logs Screen
Create a React Native screen titled "AI Chat Log" displaying all interaction logs in a scrollable view, clearly formatted.

🖥️ User Interface Requirements

1. Document Upload Screen
   Clear button labeled "Upload DOC file".

Status message indicating upload success or failure.

2. Document Text Editor Screen
   Scrollable text area displaying uploaded document text for editing.

Button labeled "Read AI" clearly indicating submission for AI training.

3. AI Chat Log Screen
   Scrollable and clear chronological logs:

yaml
Copy
Edit
📅 23 May 2025 | 🕓 12:30 PM
📱 +91-XXXXXXXXXX
❓ Caller: "Your question here"
💬 AI: "Your AI-generated reply here"
🔒 Permissions and Security
Ensure the following permissions are requested explicitly and clearly explained to users:

READ_EXTERNAL_STORAGE / WRITE_EXTERNAL_STORAGE for document handling.

SEND_SMS and READ_SMS for message interaction.

READ_CALL_LOG and READ_PHONE_STATE for missed call detection.

🚨 Edge Cases & Error Handling
Document Parsing Errors:

Clearly inform the user if document parsing fails and prompt re-upload.

AI API Failure:

Notify the user and provide retry options.

Irrelevant SMS Questions:

AI explicitly replies with provided fallback message.

Permissions Denied:

Prompt the user clearly to grant necessary permissions.

⚡ Performance Considerations
Optimize SMS response latency (aim for responses within seconds).

Optimize AI embedding storage/retrieval for rapid responses.

Ensure robust background handling to maintain responsiveness even when the app is in a killed or background state.

📦 Deliverables for Cursor AI
Cursor AI should clearly generate and deliver the following modules separately:

React Native (JavaScript/TypeScript):

Document Picker Integration.

Editable TextInput Interface.

AsyncStorage management.

API integration for AI training and response generation.

Interaction Logs UI.

Android Native Modules (Java/Kotlin):

.doc file parsing to text.

Missed Call detection and SMS auto-response handling.

SMS send/receive logic using native Android APIs.

Foreground services for persistent background functionality.
