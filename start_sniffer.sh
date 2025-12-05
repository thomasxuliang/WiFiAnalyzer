#!/bin/bash

read -p "请输入设备序列号 (adb devices): " DEVICE_ID
REQ_FILE="/sdcard/Android/data/com.vrem.wifianalyzer/files/sniffer.req"

echo ""
echo "[监听模式] 正在等待 App 发送抓包请求..."
echo "正在轮询文件: $REQ_FILE"
echo "按 Ctrl+C 退出脚本。"

while true; do
    # Check if request file exists
    if adb -s "$DEVICE_ID" shell ls "$REQ_FILE" >/dev/null 2>&1; then
        echo ""
        echo "[检测到请求] 正在读取参数..."
        
        # Read parameters to a temp file locally
        adb -s "$DEVICE_ID" shell cat "$REQ_FILE" > temp_req.txt
        
        # Parse parameters manually to avoid 'source' security risks/issues
        ACTION=$(grep "ACTION=" temp_req.txt | cut -d'=' -f2 | tr -d '\r')
        CHANNEL=$(grep "CHANNEL=" temp_req.txt | cut -d'=' -f2 | tr -d '\r')
        BANDWIDTH=$(grep "BANDWIDTH=" temp_req.txt | cut -d'=' -f2 | tr -d '\r')
        
        rm temp_req.txt

        echo "收到动作: $ACTION"
        
        if [ "$ACTION" == "START" ]; then
            echo "信道: $CHANNEL"
            echo "带宽: $BANDWIDTH"
            
            echo ""
            echo "[1] 正在设置 Root 和 Remount..."
            adb -s "$DEVICE_ID" root
            adb -s "$DEVICE_ID" remount

            echo ""
            echo "[2] 正在禁用 WiFi 服务..."
            adb -s "$DEVICE_ID" shell svc wifi disable
            adb -s "$DEVICE_ID" shell ifconfig wlan0 down

            echo ""
            echo "[3] 正在重新加载驱动至 Monitor 模式..."
            adb -s "$DEVICE_ID" shell rmmod qca_cld3_wcn7750
            sleep 2
            adb -s "$DEVICE_ID" shell insmod /vendor/lib/modules/qca_cld3_wcn7750.ko con_mode=4
            sleep 2

            echo ""
            echo "[4] 正在启动 wlan0 接口..."
            adb -s "$DEVICE_ID" shell ifconfig wlan0 up
            sleep 2

            FREQ=0
            if [ "$CHANNEL" -le 14 ]; then
                FREQ=$((CHANNEL * 5 + 2407))
            else
                FREQ=$((CHANNEL * 5 + 5000))
            fi

            BW_VAL=0
            if [ "$BANDWIDTH" -eq 40 ]; then
                BW_VAL=1
            elif [ "$BANDWIDTH" -eq 80 ]; then
                BW_VAL=2
            elif [ "$BANDWIDTH" -eq 160 ]; then
                BW_VAL=3
            fi

            echo ""
            echo "[5] 正在设置监听信道 $CHANNEL (${FREQ}MHz), 带宽 ${BANDWIDTH}MHz..."
            adb -s "$DEVICE_ID" shell "echo $FREQ $BW_VAL > /sys/class/net/wlan0/monitor_mode_channel"
            sleep 2

            FILENAME="sniffer_chan${CHANNEL}_bw${BANDWIDTH}.pcap"
            echo ""
            echo "[6] 正在启动 tcpdump，抓包文件将保存到 /data/local/temp/$FILENAME..."
            
            # Start tcpdump in background so loop continues to listen for STOP
            # Using nohup to ensure it stays running? 
            # Actually, simple backgrounding & works for the script's lifetime.
            # We open a new terminal window for tcpdump if possible, or just run in background.
            # For compatibility, running in background is easiest.
            
            adb -s "$DEVICE_ID" shell "tcpdump -i wlan0 -v -w /data/local/temp/$FILENAME" &
            TCPDUMP_PID=$!
            echo "Tcpdump (ADB PID $TCPDUMP_PID) started in background."
        fi
        
        if [ "$ACTION" == "STOP" ]; then
            echo ""
            echo "[停止] 正在停止抓包..."
            adb -s "$DEVICE_ID" shell "pkill -SIGINT tcpdump"
            echo "抓包已停止。"
            # Wait for tcpdump to exit cleanly?
        fi

        # Delete the request file to acknowledge
        adb -s "$DEVICE_ID" shell rm "$REQ_FILE"
        echo "[完成] 请求处理完毕。"
    fi

    sleep 1
done
