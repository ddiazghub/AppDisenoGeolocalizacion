package com.example.bletest

class SocketOptions {
    private var RemoteHosts: Array<String> = arrayOf("34.221.26.86", "54.189.190.202", "54.71.123.233", "52.43.44.128", "10.121.64.123")
    private var RemotePort: Int = 50000

    constructor()

    fun getHosts():Array<String> {
        return this.RemoteHosts
    }

    fun getPort():Int {
        return this.RemotePort
    }

    fun setCustomHost(host: String) {
        this.RemoteHosts[4] = host
    }
}