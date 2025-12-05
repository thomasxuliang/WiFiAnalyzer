adb -s 30d91088 shell rmmod qca_cld3_wcn7750
timeout 1
adb -s 30d91088 shell insmod /vendor/lib/modules/qca_cld3_wcn7750.ko
timeout 2
adb -s 30d91088 shell ifconfig wlan0 up
timeout 1
adb -s 30d91088 shell svc wifi enable
