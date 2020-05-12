package com.cloudbees.flow.plugins.gcp.compute

import spock.lang.Shared
import spock.lang.Unroll


class DynamicDropdownHandlerTest extends SpecHelper {
    @Shared DynamicDropdownHandler handler

    def setupSpec() {
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
        if (project != projectl) {
            assert families.size() > 0
        }
        where:
        project<< handler.listImageProject().collect { it.value }
    }

    def 'list projects'() {
        when:
        def projects = handler.listImageProject()
        then:
        assert projects
    }
}
