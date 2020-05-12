package com.cloudbees.flow.plugins.gcp.compute

import groovy.transform.builder.Builder

@Builder
class ListInstancesParameters {
    String filter
    long maxResults
    String orderBy
}
