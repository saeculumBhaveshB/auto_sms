import { NativeModules } from "react-native";

const { DocParserModule } = NativeModules;

interface DocParserInterface {
  parseDocument(uri: string): Promise<string>;
}

class DocParserService {
  /**
   * Parse a document file and extract text content
   */
  async parseDocument(uri: string): Promise<string> {
    if (!DocParserModule) {
      throw new Error("DocParserModule is not available");
    }

    try {
      return await DocParserModule.parseDocument(uri);
    } catch (error) {
      console.error("Error parsing document:", error);
      throw error;
    }
  }
}

export default new DocParserService();
