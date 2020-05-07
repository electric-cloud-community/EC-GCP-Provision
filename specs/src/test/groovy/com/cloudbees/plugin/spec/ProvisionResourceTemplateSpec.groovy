package com.cloudbees.plugin.spec

import spock.lang.Shared

class ProvisionResourceTemplateSpec extends PluginTestHelper {
    @Shared String projName = 'gcp-provision-specs'

    def setupSpec() {
        createConfig(CONFIG_NAME)
    }
    def 'provision res template'() {
        setup:
        def name = randomize("template")
        dslFile("dsl/resTemplate.dsl", [
            config: CONFIG_NAME,
            projName: projName,
            name: name,
            envTemplateName: name
        ])
        when:
        def envName = randomize('test-env')
        def result = provisionEnvironment(projName, name, envName)
        assert result.outcome == 'success'
        then:
        assert true
    }
}
