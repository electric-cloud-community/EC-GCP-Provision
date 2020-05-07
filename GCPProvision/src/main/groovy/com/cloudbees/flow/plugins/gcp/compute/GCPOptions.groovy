package com.cloudbees.flow.plugins.gcp.compute

import com.cloudbees.flow.plugins.gcp.compute.logger.Logger
import groovy.transform.builder.Builder

@Builder
class GCPOptions {
    String projectId
    String zone
    String key
    boolean ignoreSsl
    Logger logger
}
