package com.cloudbees.plugin.spec

class CreateConfigurationTest extends PluginTestHelper {

    def 'create config'() {
        when:
        def name = new Random().nextInt(9999999) + " test config"
        println getKey()
        createPluginConfiguration(pluginName, name, [checkConnection: '1', projectId: getProjectId(), zone: getZone()], "admin", getKey())
        then:
        assert true
        cleanup:
        deleteConfiguration(pluginName, name)
    }

    def 'create config wrong token'() {
        when:
        createPluginConfiguration(pluginName, 'wrong config', [checkConnection: '1', projectId: getProjectId(), zone: getZone()], username: 'test', 'token')
        then:
        thrown RuntimeException
    }
}
