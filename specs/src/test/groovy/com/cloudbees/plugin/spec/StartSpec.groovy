package com.cloudbees.plugin.spec

import com.cloudbees.flow.plugins.gcp.compute.GCP
import com.google.api.services.compute.model.Instance
import spock.lang.Ignore
import spock.lang.Shared

@Ignore("Takes too long, not too important")
class StartSpec extends PluginTestHelper {
    static String procedureName = 'Start Instances'
    @Shared
    GCP gcp = buildGCP()
    @Shared
    Instance instance1
    @Shared
    Instance instance2

    def setupSpec() {
        switchAdmin()
        createConfig(CONFIG_NAME)

        dslFile("dsl/procedure.dsl", [
            projectName  : projectName,
            procedureName: procedureName,
            params       : [config        : CONFIG_NAME,
                            instanceNames : '',
                            resultProperty: '/myJob/result'
            ]]
        )
        instance1 = provisionSample(false)
        instance2 = provisionSample(false)
        sleep(40 * 1000)
        gcp.blockUntilComplete(gcp.stopInstance(instance1.getName()), 20 * 1000)
        gcp.blockUntilComplete(gcp.stopInstance(instance2.getName()), 20 * 1000)
        switchUser()
    }

    def cleanupSpec() {
        switchAdmin()
        gcp.deleteInstance(instance1.getName())
        gcp.deleteInstance(instance2.getName())
    }

    def 'start instance'() {
        when:
        def result = runProcedure(projectName, procedureName, [
            instanceNames: instance1.getName()
        ])
        then:
        assert result.outcome == 'success'
        assert instance1.getStatus() == 'RUNNING'
    }

    def 'start two instances'() {
        when:
        def result = runProcedure(projectName, procedureName, [
            instanceNames: "${instance1.getName()}\n${instance2.getName()}"
        ])
        then:
        assert result.outcome == 'success'
    }
}
