package com.cloudbees.plugin.spec

import spock.lang.Shared
import spock.lang.Stepwise

@Stepwise
class ProvisionResourceTemplateSpec extends PluginTestHelper {
    @Shared String projName = 'gcp-provision-specs'
    @Shared String envName = randomize('test-env')

    def setupSpec() {
        switchAdmin()
        createConfig(CONFIG_NAME)

        dslFile("dsl/resTemplate.dsl", [
            config: CONFIG_NAME,
            projName: projName,
            name: envName,
            envTemplateName: envName
        ])

        switchUser()


    }

    def cleanupSpec() {
        switchAdmin()
    }

    def 'provision res template'() {
        when:
        def result = provisionEnvironment(projName, envName, envName)
        then:
        assert result.outcome == 'success'

    }

    def 'teardown environemnt'() {
        when:
        def result = tearDownEnvironment(projName, envName)
        then:
        assert result.outcome == 'success'
    }

}
