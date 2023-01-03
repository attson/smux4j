package com.attson.smux4j.session.exception

class GoAwayException: RuntimeException("stream id overflows, should start a new connection")