# Scanner Service Summary

* [title]: Wi-Fi Scanner Service
* [description]: The central component responsible for managing Wi-Fi scanning operations, processing results, and notifying registered listeners (UI components). It handles the lifecycle of the scanning process (start, stop, pause, resume) and coordinates interactions between the Android system Wi-Fi manager and the application's data model.
* [scenario]: 
    *   **Initialization**: Initialized in `MainContext` and used by `MainActivity`.
    *   **Lifecycle**: 
        *   `resume()` is called when the app/activity resumes, starting the periodic scan.
        *   `pause()` is called when the app/activity pauses, stopping the periodic scan to save battery.
        *   `stop()` is called when the app/activity stops, cleaning up resources and potentially turning off Wi-Fi (if configured).
    *   **Scanning Loop**: `PeriodicScan` triggers `Scanner.update()` at defined intervals.
    *   **Data Flow**: 
        1.  `Scanner.update()` triggers `WiFiManagerWrapper.startScan()`.
        2.  Results are received (via `ScanResultsReceiver` or direct callback).
        3.  `Transformer` converts raw Android scan results into `WiFiData` domain objects.
        4.  `Scanner` notifies all registered `UpdateNotifier`s (e.g., UI fragments) with the new `WiFiData`.

* [debug]:
    *   `updateNotifiers.size`: Check if UI components are correctly registered/unregistered.
    *   `periodicScan.running`: Verify if the scanning loop is active.
    *   `wiFiData`: Inspect the transformed data to ensure correctness of signal strength, SSID, etc.

* [callship]
    ```
    MainActivity
    └── onResume()
        └── Scanner.resume()
            └── PeriodicScan.start()
                └── nextRun() -> Handler.postDelayed(run)
    
    PeriodicScan.run()
    └── Scanner.update()
        ├── WiFiManagerWrapper.enableWiFi()
        ├── WiFiManagerWrapper.startScan()
        ├── Transformer.transformToWiFiData()
        └── [Loop] UpdateNotifier.update(wiFiData)
            └── UI Components (e.g., AccessPointsFragment, ChannelGraphFragment)
    ```

* [graph]
    ```mermaid
    classDiagram
        class ScannerService {
            <<interface>>
            +update()
            +pause()
            +resume()
            +stop()
            +register(UpdateNotifier)
            +unregister(UpdateNotifier)
            +wiFiData() WiFiData
        }

        class Scanner {
            -updateNotifiers List~UpdateNotifier~
            -wiFiData WiFiData
            -periodicScan PeriodicScan
            +update()
            +resume()
            +pause()
        }

        class PeriodicScan {
            -handler Handler
            -running Boolean
            +start()
            +stop()
            +run()
        }

        class WiFiManagerWrapper {
            +startScan()
            +scanResults()
            +enableWiFi()
        }

        class Transformer {
            +transformToWiFiData() WiFiData
        }

        class UpdateNotifier {
            <<interface>>
            +update(WiFiData)
        }

        ScannerService <|.. Scanner
        Scanner --> PeriodicScan
        Scanner --> WiFiManagerWrapper
        Scanner --> Transformer
        Scanner --> UpdateNotifier : notifies
        PeriodicScan --> Scanner : calls update()
