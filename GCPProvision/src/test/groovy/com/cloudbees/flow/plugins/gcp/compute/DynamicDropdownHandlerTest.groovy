package com.cloudbees.flow.plugins.gcp.compute

import spock.lang.Unroll


class DynamicDropdownHandlerTest extends SpecHelper {
    DynamicDropdownHandler handler

    def setup() {
        handler =  DynamicDropdownHandler.getInstance([
            configurationParameters: [
                zone: zone,
            ],
            credential: [[password: key]]
        ])
    }

    def "ListTypes"() {
        when:
        def types = handler.listTypes()
        then:
        assert types.find { it.value == 'f1-micro' }
    }

    def 'list networks'() {
        when:
        def networks = handler.listNetworks()
        then:
        assert networks
    }

    def 'list subnetworks'() {
        when:
        def subnetworks = handler.listSubnetworks('default')
        then:
        assert subnetworks.find { it.name == 'default' }
    }

    @Unroll
    def 'list families from #project'() {
        when:
        def families = handler.listFamilies(project)
        then:
        assert families.size()
        where:
        project << ['windows-cloud', 'centos-cloud']
    }
}
