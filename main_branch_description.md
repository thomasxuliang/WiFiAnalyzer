# Project Description: WiFiAnalyzer

WiFiAnalyzer is an Android application designed to analyze Wi-Fi networks, providing tools for signal strength monitoring, channel rating, and network troubleshooting.

## Key Features

1.  **Wi-Fi Scanning**: Real-time scanning of nearby Wi-Fi networks (2.4GHz, 5GHz, 6GHz).
2.  **Visualizations**:
    *   **Channel Graph**: Visualizes AP distribution across channels.
    *   **Time Graph**: Tracks signal strength over time.
3.  **Channel Rating**: Rates channels based on interference and congestion to recommend the best channel.
4.  **Detailed Information**: Displays SSID, BSSID, signal strength (dBm), security capabilities, and estimated distance.
5.  **Packet Capture (Sniffer)**: Captures Wi-Fi packets on rooted devices using tcpdump for analysis.
6.  **Vendor Lookup**: Identifies device manufacturers via OUI.

## Architecture Logic Diagram

```mermaid
graph TD
    User[User] --> UI[UI Layer]
    
    subgraph UI_Layer [UI Layer]
        MainActivity
        Navigation[Navigation Menu]
        
        subgraph Fragments
            AP_Frag[Access Points Fragment]
            Rating_Frag[Channel Rating Fragment]
            Graph_Frag[Channel Graph Fragment]
            Time_Frag[Time Graph Fragment]
            Sniffer_Frag[Sniffer Fragment]
        end
    end
    
    MainActivity --> Navigation
    Navigation --> AP_Frag
    Navigation --> Rating_Frag
    Navigation --> Graph_Frag
    Navigation --> Time_Frag
    Navigation --> Sniffer_Frag
    
    subgraph Core_Logic [Core Logic]
        Scanner[Wi-Fi Scanner]
        WiFiManager[Wi-Fi Manager Wrapper]
        Permission[Permission Manager]
        SnifferMgr[Packet Capture Manager]
        VendorMgr[Vendor Manager]
    end
    
    UI_Layer --> Core_Logic
    
    AP_Frag --> Scanner
    Rating_Frag --> Scanner
    Graph_Frag --> Scanner
    Time_Frag --> Scanner
    Sniffer_Frag --> SnifferMgr
    
    subgraph Data_Layer [Data Layer]
        WiFiData[Wi-Fi Data Repository]
        Settings[Settings / Preferences]
        DB[Database / Cache]
    end
    
    Scanner --> WiFiData
    SnifferMgr --> Tcpdump[Tcpdump Manager]
    Tcpdump --> Root[Root Access / Shell]
    
    Core_Logic --> Data_Layer
    
    WiFiData --> UI_Layer
    
    classDef ui fill:#f9f,stroke:#333,stroke-width:2px;
    classDef core fill:#bbf,stroke:#333,stroke-width:2px;
    classDef data fill:#dfd,stroke:#333,stroke-width:2px;
    
    class UI_Layer,MainActivity,Navigation,AP_Frag,Rating_Frag,Graph_Frag,Time_Frag,Sniffer_Frag ui;
    class Core_Logic,Scanner,WiFiManager,Permission,SnifferMgr,VendorMgr,Tcpdump core;
    class Data_Layer,WiFiData,Settings,DB data;
```

## Sniffer Feature Workflow

The Sniffer feature is a specialized component for packet capture.

```mermaid
sequenceDiagram
    participant User
    participant UI as SnifferFragment
    participant VM as SnifferViewModel
    participant Svc as SnifferService
    participant PCM as PacketCaptureManager
    participant TD as TcpdumpManager
    participant Shell as Root Shell

    User->>UI: Click Start Capture
    UI->>VM: startCapture()
    VM->>Svc: Start Service Intent
    Svc->>PCM: startCapture(config)
    PCM->>TD: startTcpdump(interface, file)
    TD->>Shell: Execute tcpdump command
    Shell-->>TD: Process Started
    TD-->>PCM: Success
    PCM-->>Svc: Update State (RUNNING)
    Svc-->>VM: Broadcast State
    VM-->>UI: Update UI (Show Stop Button)
    
    User->>UI: Click Stop Capture
    UI->>VM: stopCapture()
    VM->>Svc: Stop Service Action
    Svc->>PCM: stopCapture()
    PCM->>TD: stopTcpdump()
    TD->>Shell: Kill tcpdump process
    Shell-->>TD: Process Ended
    TD-->>PCM: Success
    PCM-->>Svc: Update State (STOPPED)
    Svc-->>VM: Broadcast State
    VM-->>UI: Update UI (Show Start Button)
