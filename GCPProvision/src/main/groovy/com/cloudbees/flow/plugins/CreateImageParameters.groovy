package com.cloudbees.flow.plugins

import groovy.transform.builder.Builder

@Builder
class CreateImageParameters {
    String sourceDisk
    String sourceImage
    String sourceSnapshot
    String family
    String description
    String sourceProject
    String zone
    long diskSizeGb
    boolean forceCreate
    String name
    List<String> locations
}
