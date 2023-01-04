package com.attson.smux4j

import com.attson.smux4j.ext.net.Conn
import com.attson.smux4j.mux.Config
import com.attson.smux4j.mux.defaultConfig
import com.attson.smux4j.mux.verifyConfig

class Session(conn: Conn, private var config: Config? = null) {

    init {
        if (config == null) {
            config = defaultConfig()
        }

        verifyConfig(config!!)
    }
}