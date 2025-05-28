# Local LLM Auto-Reply Integration Guide

This document outlines high-level steps for Cursor AI to implement a Local LLM–powered SMS auto-reply feature in a React Native + native Android application.
It assumes existing LLM code is present; Cursor AI should review, refactor or replace as needed.

---

## 1. Review and Prepare Existing LLM Module

- Gather the current native LLM integration components in the project.
- Identify how the model is being loaded, where inference is invoked, and how resources are managed.
- Note any areas where document parsing or SMS hooks are entwined with the logic, marking them for refactoring or enhancement.

---

## 2. Configure and Initialize the Local LLM Runtime

- Choose the local inference engine (llama.cpp or MLC LLM) best suited for on-device performance.
- Ensure the application is configured to include the necessary native libraries and model files.
- On enabling auto-reply, initialize the LLM model in background, preparing it for subsequent inference requests.

---

## 3. Provide Document Access to the LLM

- Maintain uploaded documents in their original format within the app’s storage.
- Enable the LLM runtime to access and parse these files at inference time, without upfront text conversion.
- Implement a document registry that the LLM engine can query to locate and read files dynamically.

---

## 4. Build the Inference Process

- When a new SMS arrives (after a missed call), extract the text of the SMS message.
- Pass the SMS text and references to the uploaded documents into the LLM inference pipeline.
- Allow the LLM to read from the documents, interpret the content, and generate a coherent answer.

---

## 5. Integrate with SMS Receiver for Auto-Reply

- In the SMS listener logic, replace any static reply behavior with a call to the inference process.
- Once the LLM returns its answer, format the response by prefixing it with “AI:”.
- If the LLM cannot formulate a relevant answer, fallback to a generic message indicating inability to answer.
- If the LLM initialization or document access fails due to lack of resources or offline state, send a predefined “not available” message.

---

## 6. Lifecycle and Resource Management

- Activate the LLM runtime only while auto-reply is enabled to conserve device memory and CPU.
- Gracefully shut down or pause the LLM when auto-reply is turned off or the app is closed.
- Ensure inference tasks run in background threads or services, keeping the UI responsive.

---

## 7. User Controls and Feedback

- Provide a simple toggle in the React Native interface to enable or disable LLM-powered auto-replies.
- Display real-time status messages: auto-reply on/off, inference success or fallback, and any errors encountered.

---

## 8. Validation and Quality Assurance

- Test with a variety of document types and content to confirm accurate LLM-based replies.
- Validate fallback messages for out-of-scope questions and offline conditions.
- Monitor performance metrics (latency, memory usage) to ensure a smooth user experience.

---

**Implementation Note:**
Cursor AI should interpret these high-level steps into the required native and React Native integrations, ensuring that no explicit code samples are embedded in the document itself.
