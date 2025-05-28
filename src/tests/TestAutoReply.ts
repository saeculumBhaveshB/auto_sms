import { NativeModules } from "react-native";

/**
 * Test suite for Auto-Reply feature.
 *
 * Note: These are basic unit tests that mock the native modules.
 * For complete testing, you'll need to test on an actual device
 * with real missed calls and SMS.
 */

describe("Auto Reply Feature", () => {
  const mockAutoReplyModule = {
    isAutoReplyEnabled: jest.fn().mockResolvedValue(false),
    setAutoReplyEnabled: jest.fn().mockResolvedValue(true),
    getMissedCallNumbers: jest.fn().mockResolvedValue([]),
    clearMissedCallNumbers: jest.fn().mockResolvedValue(true),
  };

  // Mock the native modules
  NativeModules.AutoReplyModule = mockAutoReplyModule;

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("should call isAutoReplyEnabled correctly", async () => {
    const result = await NativeModules.AutoReplyModule.isAutoReplyEnabled();

    expect(mockAutoReplyModule.isAutoReplyEnabled).toHaveBeenCalledTimes(1);
    expect(result).toBe(false);
  });

  it("should call setAutoReplyEnabled with correct params", async () => {
    const enabled = true;
    await NativeModules.AutoReplyModule.setAutoReplyEnabled(enabled);

    expect(mockAutoReplyModule.setAutoReplyEnabled).toHaveBeenCalledTimes(1);
    expect(mockAutoReplyModule.setAutoReplyEnabled).toHaveBeenCalledWith(
      enabled
    );
  });

  it("should call getMissedCallNumbers correctly", async () => {
    const mockData = [{ phoneNumber: "+1234567890", timestamp: Date.now() }];

    mockAutoReplyModule.getMissedCallNumbers.mockResolvedValueOnce(mockData);

    const result = await NativeModules.AutoReplyModule.getMissedCallNumbers();

    expect(mockAutoReplyModule.getMissedCallNumbers).toHaveBeenCalledTimes(1);
    expect(result).toEqual(mockData);
  });

  it("should call clearMissedCallNumbers correctly", async () => {
    await NativeModules.AutoReplyModule.clearMissedCallNumbers();

    expect(mockAutoReplyModule.clearMissedCallNumbers).toHaveBeenCalledTimes(1);
  });
});
