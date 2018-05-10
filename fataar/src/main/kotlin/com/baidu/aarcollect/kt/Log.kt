package com.baidu.aarcollect.kt

fun aarLog(tag:String = "AAR Log" ,msg: String = "finish") {
    format(tag, msg)
}

fun aarWarningLog(msg: String = "finish") {
    format("warning", msg)
}

fun aarInfoLog(msg: String = "finish") {
    format("info", msg)
}

fun format(tag:String , msg: String) {
    println("====== [aar Collect] ==== \n [ $tag ] : [ $msg ]")
}