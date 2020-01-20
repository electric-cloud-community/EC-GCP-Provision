package com.cloudbees.flow.plugins

import groovy.transform.builder.Builder

@Builder
class ProvisionInstanceParameters {
    String instanceName
    String instanceType
    List<String> tags
    String sourceImage
    String network
    List<ProvisionInstanceKey> keys
    String subnetwork
    long diskSizeGb
    String projectId
    String regionName
    String zoneName
    boolean assignPublicIp
}