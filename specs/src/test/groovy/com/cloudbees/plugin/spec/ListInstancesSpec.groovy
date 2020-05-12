package com.cloudbees.plugin.spec

class ListInstancesSpec extends PluginTestHelper {
    static String procedureName = 'List Instances'

    def setupSpec() {
        switchAdmin()
        createConfig(CONFIG_NAME)
        dslFile("dsl/procedure.dsl", [
            projectName  : projectName,
            procedureName: procedureName,
            params       : [config        : CONFIG_NAME,
                            filter        : '',
                            maxResults    : '',
                            orderBy       : '',
                            resultProperty: '/myJob/result'
            ]]
        )
        switchUser()
        ignoreDepCache(true)
    }

    def cleanupSpec() {
        switchAdmin()
    }

    def 'list instances'() {
        when:
        def result = runProcedure(projectName, procedureName, [:])
        then:
        assert result.outcome == 'success'
    }
}
