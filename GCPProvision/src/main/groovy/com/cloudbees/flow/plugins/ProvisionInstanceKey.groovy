package com.cloudbees.flow.plugins

import groovy.transform.builder.Builder

@Builder
class ProvisionInstanceKey {
    String userName
    String key
}
