package com.cloudbees.plugin.spec

import com.cloudbees.flow.plugins.gcp.compute.GCP
import spock.lang.Shared

class ProvisionSpec extends PluginTestHelper {
    static String procedureName = 'Provision'
    @Shared
    String familyProject = 'debian-cloud'
    @Shared
    String family = 'debian-10'
    @Shared
    def diskSize = 20
    @Shared
    def network = 'default'
    @Shared
    def subnetwork = 'default'
    @Shared
    GCP gcp = buildGCP()

    def setupSpec() {
        switchAdmin()
        createConfig(CONFIG_NAME)

        dslFile("dsl/procedure.dsl", [
            projectName  : projectName,
            procedureName: procedureName,
            params       : [config              : CONFIG_NAME,
                            instanceNameTemplate: 'spec-instance',
                            instanceType        : 'f1-micro',
                            sourceImage         : '',
                            sourceImageProject  : 'debian-cloud',
                            sourceImageFamily   : 'debian-10',
                            keys                : '',
                            network             : 'default',
                            subnetwork          : 'default',
                            diskSize            : '',
                            instanceTags        : '',
                            assignPublicIp      : '',
                            useServiceAccount   : '',
                            serviceAccountEmail : '',
                            deletionProtection  : '',
                            instanceHostname    : '',
                            count               : '1',
                            pingResource        : '',
                            waitTimeout         : '300',
                            resourcePort        : '',
                            resourceWorkspace   : '',
                            resourceZone        : '',
                            resultProperty      : '/myJob/result'
            ]]
        )

        switchUser()
    }

    def cleanupSpec() {
        switchAdmin()
    }

    def 'provision simple instance'() {
        when:
        def result = runProcedure(projectName, procedureName,
            [
                resPoolName: 'spec-res-pool'
            ])
        then:
        assert result.outcome == 'success'
        String name = getJobProperty('/myJob/result/instanceName', result.jobId)
        def instance = gcp.getInstance(name)
        def res = getResource(name)
        assert res
        assert res.resource?.hostName == gcp.getInstanceInternalIp(name)
        assert instance.getNetworkInterfaces().first().getNetwork() =~ /default/
        cleanup:
        gcp.deleteInstance(name)
        deleteResource(name)
    }

    def 'provision instance with public ip'() {
        when:
        def result = runProcedure(projectName, procedureName,
            [
                resPoolName: 'spec-res-pool',
                assignPublicIp: 'true'
            ])
        then:
        assert result.outcome == 'success'
        String name = getJobProperty('/myJob/result/instanceName', result.jobId)
        def instance = gcp.getInstance(name)
        def res = getResource(name)
        assert res
        assert res.resource?.hostName == gcp.getInstanceInternalIp(name)
        assert gcp.getInstanceExternalIp(name)
        assert instance.getNetworkInterfaces().first().getNetwork() =~ /default/
        cleanup:
        gcp.deleteInstance(name)
        deleteResource(name)
    }

    def 'provision instance with same service account'() {
        when:
        def result = runProcedure(projectName, procedureName,
            [
                useServiceAccount: 'sameAccount'
            ])
        then:
        assert result.outcome == 'success'
        String name = getJobProperty('/myJob/result/instanceName', result.jobId)
        assert name
        def instance = gcp.getInstance(name)
        assert instance.getServiceAccounts().first().getEmail()
        cleanup:
        gcp.deleteInstance(name)
    }

    def 'provision instance with keys'() {
        when:
        def result = runProcedure(projectName, procedureName,
            [
                keys: '[{"userName": "imago", "key": "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQCwQKibIYqBk8G2994+jRc+lmh2KKNp1zfjJK4BXEpoD7D5ie0WNpE3KxVA+R8YGUVIQXwo1ZgPKRSGSolr9PE0OBXYHKcgzYyPeXgtMPQvnAYYkrdaMqVQmclJkpLA6nHHmM4ObswFB+t9NS7XQnGYGwI05dakaCA4NiYpwUAQb9GR0WfpZY495em0NSihn8+JjDEtPKAkNyNDk9ur+w+3w+S8Lv0xc/xdS6GgJmNJr9SZaLTrxAlB0FVCHrwo3CBFCDpFSQZYA44ZxMB7K8FsFfcv73k7DlF7jB+nQinNFx8MPec0oaiVvMBtVtPMiC43O/lvhlDQEYP1Z6qPk8NUvtoYqdqllJDAOJ29XyHFjS9gQnKYa7fevAMflRJs2aQLfHpdoiM+S+RLvdzCHgKdTK+qnZeDeWnnmTBVfA9pdKl3LBFEwseV8PyhAJG0WiW9WFKJKe0w7BOMhwO+n27t1PnKz9yOfDRLJFKhYYcXZoE91QXTqDKCzGEfg9Mpyis= imago@polinas-macbook"}]'
            ])
        then:
        assert result.outcome == 'success'
        String name = getJobProperty('/myJob/result/instanceName', result.jobId)
        assert name
        def instance = gcp.getInstance(name)
        assert instance.getMetadata().getItems().find { it.getKey() == 'ssh-keys'}
        cleanup:
        gcp.deleteInstance(name)
    }


    def getResource(name) {
        dsl "getResource resourceName: '$name'"
    }

    def deleteResource(name) {
        try {
            dsl "deleteResource resourceName: '$name'"
        } catch (Throwable ignore) {

        }
    }

    def 'validate networks dropdown'() {
        when:
        def options = getFormalParameterOptions(pluginName, procedureName, 'network', [config: CONFIG_NAME])
        then:
        assert options.size() > 0
        assert options.find { it.value == "default" }
    }

    def 'validate subnetworks dropdown'() {
        when:
        def options = getFormalParameterOptions(pluginName, procedureName, 'subnetwork'
            , [config: CONFIG_NAME, network: 'default'])
        then:
        assert options.size() > 0
        assert options.find { it.value == "default" }
    }

    def 'validate families projects'() {
        when:
        def options = getFormalParameterOptions(pluginName, procedureName, 'sourceImageProject'
            , [config: CONFIG_NAME])
        then:
        assert options.size() > 0
        assert options.find { it.value =~ /debian/ }
    }

    def 'validate families'() {
        when:
        def options = getFormalParameterOptions(pluginName, procedureName, 'sourceImageFamily'
            , [config: CONFIG_NAME, sourceImageProject: familyProject])
        then:
        assert options.size() > 0
        assert options.find { it.value =~ /debian/ }
    }

    def 'validate types'() {
        when:
        def options = getFormalParameterOptions(pluginName, procedureName, 'instanceType'
            , [config: CONFIG_NAME])
        then:
        assert options.size() > 0
        assert options.find { it.value =~ /micro/ }

    }
}
