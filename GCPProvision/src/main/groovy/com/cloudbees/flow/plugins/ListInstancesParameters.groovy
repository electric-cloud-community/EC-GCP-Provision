package com.cloudbees.flow.plugins

import groovy.transform.builder.Builder

@Builder
class ListInstancesParameters {
    String filter
    long maxResults
    String orderBy
}
