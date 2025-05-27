Project Requirements Document

Feature: Document Upload and Local LLM Integration for Automated SMS Response

Overview

The application will support uploading multiple document formats (e.g., PDF, DOCX, TXT) directly into the application without converting them into plain text initially. When a caller sends an SMS question after missing a call, a locally running LLM (e.g., via llama.cpp or MLC LLM) will automatically generate responses based on the uploaded documents.

Functional Requirements

1. Document Upload

Users can upload multiple documents simultaneously.

Supported formats include PDF, DOCX, and TXT.

The documents must be stored in their original format within the application's local storage without converting to plain text upfront.

2. Local LLM Integration

The application will utilize llama.cpp or MLC LLM to run a quantized local LLM model directly on the user's device.

The model will have the capability to interpret and generate answers directly from the documents in their native format.

The LLM integration must function entirely offline without reliance on external services or APIs.

3. Automated SMS Response

The app will listen for incoming SMS questions triggered after a missed call event.

Incoming questions are processed by the local LLM, utilizing the uploaded documents as the knowledge base.

The LLM will generate contextually relevant answers directly from the documents.

If the LLM cannot find a relevant answer, the default response should be:

AI: Sorry, I am not capable of giving this answer. Wait for a call or try with a different question.

All responses generated must prefix with the tag "AI:".

Technical Requirements

Storage Management

Implement secure local storage management for uploaded documents within the app directory.

LLM Runtime

Set up the llama.cpp or MLC LLM environment configured to run on Android.

Ensure optimization of LLM performance, considering mobile device hardware constraints (CPU, memory usage).

SMS Handling and Background Processing

Implement a background Android service to:

Listen for SMS events.

Feed the received SMS question directly into the LLM.

Send automated SMS replies based on generated responses.

User Interface Requirements

Provide a simple and intuitive interface for document upload.

Display statuses on-screen clearly indicating:

The caller's phone number.

The question received.

The AI-generated response.

Privacy and Security

Ensure all processes (document storage, SMS handling, LLM inference) run entirely on-device.

No user data or uploaded document content should leave the user's device at any time.

Testing and Validation

Verify LLM functionality with various document types and content complexity.

Test system responses for accuracy, relevance, and proper fallback behavior when answers are unavailable.
