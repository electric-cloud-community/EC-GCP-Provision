package com.cloudbees.flow.plugins

import com.google.api.services.compute.model.Image
import groovy.transform.builder.Builder

@Builder
class ProvisionInstanceParameters {
    String instanceName
    String instanceType
    List<String> tags
    String sourceImageUrl
    String network
    List<ProvisionInstanceKey> keys
    String subnetwork
    long diskSizeGb
    String projectId
    String regionName
    String zoneName
    boolean assignPublicIp
    Image sourceImage
    String description
    boolean deletionProtection
    String hostname
    String serviceAccountEmail
    ServiceAccountType serviceAccountType
}

enum ServiceAccountType {
    NO_ACCOUNT,
    SAME,
    DEFINED
}