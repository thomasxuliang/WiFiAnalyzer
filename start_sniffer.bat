@echo off
setlocal EnableDelayedExpansion
chcp 65001 > nul

REM Auto-detect device (assume only one device connected)
for /f "tokens=1" %%i in ('adb devices ^| findstr /r "device$"') do set DEVICE_ID=%%i
if "%DEVICE_ID%"=="" (
    echo ERROR: No device found. Please connect a device via USB.
    pause
    exit /b 1
)

echo Using device: %DEVICE_ID%
set REQ_FILE=/sdcard/Android/data/com.vrem.wifianalyzer.BETA/files/sniffer.req

echo.
echo [Monitor Mode] Waiting for App request...
echo Polling file: %REQ_FILE%
echo Press Ctrl+C to exit.

:LOOP
    REM Check if request file exists
    echo [DEBUG] Checking for file... (Press Ctrl+C to stop)
    adb -s %DEVICE_ID% shell ls %REQ_FILE% 2>&1
    set CHECK_RESULT=%ERRORLEVEL%
    echo [DEBUG] File check result: %CHECK_RESULT%
    
    if %CHECK_RESULT% EQU 0 (
        echo [DEBUG] File found! Processing request...
        call :PROCESS_REQUEST
    )
    timeout /t 2 > nul
goto LOOP

:PROCESS_REQUEST
    echo.
    echo [Request Detected] Reading parameters...
    
    REM Read parameters to a temp file locally
    adb -s %DEVICE_ID% shell cat %REQ_FILE% > temp_req.txt
    
    REM Parse parameters
    set "ACTION="
    set "CHANNEL="
    set "BANDWIDTH="
    
    for /f "tokens=1,2 delims==" %%a in (temp_req.txt) do (
        if "%%a"=="ACTION" set "ACTION=%%b"
        if "%%a"=="CHANNEL" set "CHANNEL=%%b"
        if "%%a"=="BANDWIDTH" set "BANDWIDTH=%%b"
    )
    
    REM Clean up temp file
    if exist temp_req.txt del temp_req.txt

    REM Check if variables are empty
    if "!ACTION!"=="" (
        echo Failed to parse parameters.
        goto :EOF
    )

    echo Received Action: !ACTION!
    
    if "!ACTION!"=="START" (
        call :START_SNIFFER
    )
    
    if "!ACTION!"=="STOP" (
        call :STOP_SNIFFER
    )

    REM Delete the request file to acknowledge
    adb -s %DEVICE_ID% shell rm %REQ_FILE%
    echo [Done] Request processed.
goto :EOF

:START_SNIFFER
    echo.
    echo ========================================
    echo Starting Capture on Channel !CHANNEL!, Bandwidth !BANDWIDTH!MHz
    echo ========================================
    
    echo.
    echo [1] Disabling WiFi Service...
    adb -s %DEVICE_ID% shell svc wifi disable
    adb -s %DEVICE_ID% shell ifconfig wlan0 down

    echo.
    echo [2] Reloading Driver to Monitor Mode...
    adb -s %DEVICE_ID% shell rmmod qca_cld3_wcn7750
    adb -s %DEVICE_ID% shell insmod /vendor/lib/modules/qca_cld3_wcn7750.ko con_mode=4
    timeout /t 2 > nul

    echo.
    echo [3] Bringing wlan0 UP...
    adb -s %DEVICE_ID% shell ifconfig wlan0 up

    set /a FREQ=0
    if !CHANNEL! LEQ 14 (
        set /a FREQ=!CHANNEL! * 5 + 2407
    ) else (
        set /a FREQ=!CHANNEL! * 5 + 5000
    )

    set BW_VAL=0
    if "!BANDWIDTH!"=="40" set BW_VAL=1
    if "!BANDWIDTH!"=="80" set BW_VAL=2
    if "!BANDWIDTH!"=="160" set BW_VAL=3

    echo.
    echo [4] Setting Channel !CHANNEL! (!FREQ!MHz), Bandwidth !BANDWIDTH!MHz...
    echo Writing: !FREQ! !BW_VAL!
    adb -s %DEVICE_ID% shell "echo !FREQ! !BW_VAL! > /sys/class/net/wlan0/monitor_mode_channel"
    echo Channel setting command executed.
    
    echo.
    echo [5] Verifying monitor mode configuration...
    echo Checking if channel is set correctly...
    adb -s %DEVICE_ID% shell "iw dev wlan0 info | grep -E 'type|channel|wiphy'"
    echo.
    echo Checking interface status...
    adb -s %DEVICE_ID% shell ifconfig wlan0

    REM Generate timestamp for unique filename
    for /f "tokens=1-6 delims=/:. " %%a in ("%date% %time%") do (
        set TIMESTAMP=%%a%%b%%c_%%d%%e%%f
    )
    set FILENAME=sniffer_chan!CHANNEL!_bw!BANDWIDTH!_!TIMESTAMP!.pcap
    set CURRENT_PCAP_FILE=/data/local/tmp/!FILENAME!
    
    echo.
    echo [6] Preparing capture directory...
    adb -s %DEVICE_ID% shell mkdir -p /data/local/tmp
    adb -s %DEVICE_ID% shell chmod 777 /data/local/tmp
    
    echo.
    echo [7] Starting tcpdump...
    echo     Channel: !CHANNEL!
    echo     Bandwidth: !BANDWIDTH!MHz
    echo     Frequency: !FREQ!MHz
    echo     PCAP File: /data/local/tmp/!FILENAME!
    echo.
    
    REM Start tcpdump in a new window so this script can continue loop
    REM Use /data/local/tmp instead of /data/local/temp (temp might not exist)
    REM Window will auto-close when tcpdump exits
    start "WiFi Sniffer Capture" cmd /c "adb -s %DEVICE_ID% shell tcpdump -i wlan0 -v -w /data/local/tmp/!FILENAME! & echo. & echo Capture stopped. Window will close in 3 seconds... & timeout /t 3 > nul"
goto :EOF

:STOP_SNIFFER
    echo.
    echo ========================================
    echo [STOP] Stopping capture...
    echo ========================================
    
    echo [1] Stopping tcpdump...
    REM Try SIGINT (2) first, then SIGTERM (15), then SIGKILL (9)
    adb -s %DEVICE_ID% shell "pkill -2 tcpdump"
    adb -s %DEVICE_ID% shell "pkill -15 tcpdump"
    adb -s %DEVICE_ID% shell "pkill -9 tcpdump"
    
    echo [2] Restoring WiFi to Normal Mode...
    adb -s %DEVICE_ID% shell ifconfig wlan0 down
    
    echo [3] Reloading Driver to Normal Mode...
    adb -s %DEVICE_ID% shell rmmod qca_cld3_wcn7750
    adb -s %DEVICE_ID% shell insmod /vendor/lib/modules/qca_cld3_wcn7750.ko
    timeout /t 2 > nul
    
    echo [4] Enabling WiFi Service...
    adb -s %DEVICE_ID% shell svc wifi enable
    adb -s %DEVICE_ID% shell ifconfig wlan0 up
    
    echo.
    echo [5] Pulling PCAP file from device...
    if not exist "pcap_files" mkdir pcap_files
    
    REM Pull the current capture file
    if defined CURRENT_PCAP_FILE (
        echo Pulling: !CURRENT_PCAP_FILE!
        adb -s %DEVICE_ID% pull "!CURRENT_PCAP_FILE!" pcap_files/
        echo PCAP file saved to: pcap_files\
    ) else (
        echo Warning: No current PCAP file to pull. Did you start a capture first?
    )
    
    echo.
    echo Capture stopped and WiFi restored.
    echo Ready for next capture. Press Ctrl+C to exit script.
goto :EOF
