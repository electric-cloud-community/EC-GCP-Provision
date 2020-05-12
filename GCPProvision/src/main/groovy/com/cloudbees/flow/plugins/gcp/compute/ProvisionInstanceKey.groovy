package com.cloudbees.flow.plugins.gcp.compute

import groovy.transform.builder.Builder

@Builder
class ProvisionInstanceKey {
    String userName
    String key
}
