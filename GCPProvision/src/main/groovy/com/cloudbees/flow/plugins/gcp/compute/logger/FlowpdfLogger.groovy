package com.cloudbees.flow.plugins.gcp.compute.logger

import com.cloudbees.flowpdf.Log

class FlowpdfLogger implements  Logger {
    Log log

    FlowpdfLogger(Log log) {
        this.log = log
    }
    @Override
    void debug(Object... messages) {
        log.debug(messages)
    }

    @Override
    void info(Object... messages) {
        log.info(messages)
    }

    @Override
    void trace(Object... messages) {
        log.trace(messages)
    }
}
