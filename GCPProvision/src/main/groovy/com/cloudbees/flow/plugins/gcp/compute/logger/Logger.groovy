package com.cloudbees.flow.plugins.gcp.compute.logger

interface Logger {
    void debug(Object... messages)
    void info(Object... messages)
    void trace(Object ...messages)
}
