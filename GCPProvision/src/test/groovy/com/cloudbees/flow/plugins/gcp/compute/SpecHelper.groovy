package com.cloudbees.flow.plugins.gcp.compute

import spock.lang.Specification

class SpecHelper extends Specification {

    String getKey() {
        def key = System.getenv('GCP_KEY')
        assert key
        return key
    }

    String getProject() {
        def project = System.getenv('GCP_PROJECT')
        assert project
        return project
    }

    String getZone() {
        def zone = System.getenv('ZONE') ?: 'us-east1-b'
        return zone
    }


    GCP buildGCP() {
        new GCP(GCPOptions.builder().zone(zone).key(key).build())
    }
}
