package com.cloudbees.flow.plugins.gcp.compute

import com.cloudbees.flow.plugins.gcp.compute.logger.Logger
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
        new GCP(GCPOptions.builder().zone(zone).key(key).logger(new Logger() {
            @Override
            void debug(Object... messages) {
                messages.each {
                    println '[DEBUG] ' + it
                }
            }

            @Override
            void info(Object... messages) {
                messages.each {
                    println '[INFO] ' + it
                }
            }

            @Override
            void trace(Object... messages) {
                messages.each {
                    println '[TRACE] ' + it
                }
            }
        }).build())
    }

    String randomize(String pattern) {
        pattern + '-' + new Random().nextInt()
    }
}
