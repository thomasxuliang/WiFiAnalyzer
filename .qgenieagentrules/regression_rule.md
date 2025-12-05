1.要求每修改或新增一个文件，务必执行构建和test
    ./gradlew installDebug
2.当出现build fail时先尝试revert最近修改或添加的文件并通过缩小范围找出导致build 失败的原因
