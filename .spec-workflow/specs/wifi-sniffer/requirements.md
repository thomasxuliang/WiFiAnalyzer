# Requirements Document

## Introduction

This feature adds a WiFi Sniffer capability to the WiFiAnalyzer application, specifically targeting the Qualcomm wcn7750 development device. Leveraging root access, the application will enable monitor mode and capture packets directly on the device without requiring external PC scripts.

## Alignment with Product Vision

This aligns with the goal of providing advanced diagnostic tools for developers and network engineers using specific hardware. It enables "short and fast" debugging cycles by keeping the capture workflow entirely within the Android device.

## Requirements

### Requirement 1: Root-Enabled Monitor Mode

**User Story:** As a developer with a rooted wcn7750 device, I want the app to automatically handle the complex driver reloading sequence to enable monitor mode, so that I don't have to type adb shell commands manually.

#### Acceptance Criteria

1.  WHEN the user initiates a capture, THEN the app SHALL execute the following sequence using root privileges:
    *   `svc wifi disable`
    *   `ifconfig wlan0 down`
    *   `rmmod qca_cld3_wcn7750`
    *   `insmod /vendor/lib/modules/qca_cld3_wcn7750.ko con_mode=4`
    *   `ifconfig wlan0 up`
2.  IF any step fails, THEN the app SHALL abort and report an error.

### Requirement 2: Packet Capture Control (Start/Stop)

**User Story:** As a user, I want Start and Stop buttons on the Access Points list interface, so that I can easily capture traffic for the currently viewed channel.

#### Acceptance Criteria

1.  WHEN the user is on the AP List page (or expanding an AP detail), THEN a "Start Capture" option SHALL be visible.
2.  WHEN "Start Capture" is clicked, THEN the app SHALL:
    *   Configure the channel: `echo [freq] [bw] > /sys/class/net/wlan0/monitor_mode_channel`
    *   Start `tcpdump`: `tcpdump -i wlan0 -v -w /data/local/temp/[filename].pcap`
3.  WHEN "Stop Capture" is clicked, THEN the app SHALL kill the `tcpdump` process.

### Requirement 3: File Storage

**User Story:** As a user, I want captured files to be saved with descriptive names, so that I can identify them later.

#### Acceptance Criteria

1.  The captured PCAP files SHALL be stored in `/data/local/temp/`.
2.  The filename format SHALL be `*channel_[channel]_bw_[bw].pcap` (e.g., `sniffer_chan1_bw20.pcap`).

## Non-Functional Requirements

### Code Architecture and Modularity
-   **Root Execution Isolation**: All root command execution must be encapsulated in a dedicated helper class/module.
-   **Device Specificity**: The implementation should be aware that it targets specific hardware (wcn7750) and fail gracefully on unsupported devices if possible (though primary target is the dev kit).

### Performance
-   The UI must remain responsive while the background root commands are executing (no main thread blocking).

### Security
-   The app requires Root access (`su`) and will request it.
