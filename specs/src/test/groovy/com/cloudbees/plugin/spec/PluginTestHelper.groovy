package com.cloudbees.plugin.spec

import com.cloudbees.flow.plugins.gcp.compute.GCP
import com.cloudbees.flow.plugins.gcp.compute.GCPOptions
import com.cloudbees.flow.plugins.gcp.compute.ProvisionInstanceParameters
import com.electriccloud.spec.PluginSpockTestSupport
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spock.util.concurrent.PollingConditions

class PluginTestHelper extends PluginSpockTestSupport {
    static def configId = 1
    static def pluginName = "EC-GCP-Provision"
    static final String projectName = 'Provision Spec Tests'

    static def procedureName = ""
    static String CONFIG_NAME = 'specConfig'


    def createConfig(configName) {
        createPluginConfiguration(pluginName,
            configName,
            [desc     : "test configuration", checkConnection: "0",
             projectId: getProjectId(), zone: getZone()],
            "admin", getKey())
    }

    def createInvalidConfig(configName) {
        createPluginConfiguration(pluginName, configName, [desc: "test configuration", checkConnection: "0"], "admin", 'wrong')
    }

    String getZone() {
        return System.getenv("ZONE") ?: 'us-east1-b'
    }

    String getProjectId() {
        def id = System.getenv("GCP_PROJECT")
        assert id
        return id
    }


    String getKeyClean() {
        def key = System.getenv("GCP_KEY")
        if (!key) {
            key = System.getenv('GCP_KEY_BASE64')
            assert key
            key = new String(key.decodeBase64())
            key.trim()
        }
        return key
    }

    String getKey() {
        def key = getKeyClean()
        def parsed = new JsonSlurper().parseText(key)
        JsonOutput.toJson(parsed).replaceAll(/\\n/, /\\\\n/)
    }

    def getStepSummary(def jobId, def stepName) {
        assert jobId
        def summary
        def property = "/myJob/jobSteps/RunProcedure/steps/$stepName/summary"
        try {
            summary = getJobProperty(property, jobId)
        } catch (Throwable e) {
            logger.debug("Can't retrieve Upper Step Summary from the property: '$property'; check job: " + jobId)
        }
        return summary
    }


    List<Map> getFormalParameterOptions(String pluginName, String procedureName, String parameterName, Map actualParameters) {
        String params = actualParameters.collect { k, v -> "$k: '$v'" }.join(",")
        String script = """
getFormalParameterOptions formalParameterName: '$parameterName',
    projectName: '/plugins/$pluginName/project',
    procedureName: '$procedureName',
    actualParameter: [$params]
            """
        def formalParameterOptions = dsl(script)?.option
        return formalParameterOptions
    }

    GCP buildGCP() {
        return new GCP(GCPOptions.builder().key(getKeyClean()).zone(getZone()).build())
    }

    def provisionEnvironment(projectName, templateName, environmentName) {
        def result = dsl """
            provisionEnvironment projectName: '$projectName', environmentName: '$environmentName', environmentTemplateName: '$templateName'
"""

        PollingConditions poll = createPoll(120)
        poll.eventually {
            jobStatus(result.jobId).status == 'completed'
        }
        def outcome = jobStatus(result.jobId).outcome
        def logs = readJobLogs(result.jobId)
        return [jobId: result.jobId, logs: logs, outcome: outcome]
    }

    def tearDownEnvironment(projectName, envName) {
        def result = dsl "tearDownEnvironment projectName: '$projectName', environmentName: '$envName'"
        PollingConditions poll = createPoll(240)
        poll.eventually {
            jobStatus(result.jobId).status == 'completed'
        }
        def outcome = jobStatus(result.jobId).outcome
        def logs = readJobLogs(result.jobId)
        return [jobId: result.jobId, logs: logs, outcome: outcome]
    }


    def switchUser() {
        String userName = 'gcp-provision-spec-user'

        try {
            dsl """
                createUser userName: "$userName", email: '$userName', password: "$userName"
            """
            println ":Created user $userName"
        } catch (Throwable e) {

        }
        //ACL

        def allowAll = """
 changePermissionsPrivilege = 'allow'
 executePrivilege = 'allow'
 modifyPrivilege = 'allow'
 readPrivilege = 'allow'
"""

        dsl """
aclEntry principalType: 'user', principalName: '$userName', {
    systemObjectName = 'projects'
    objectType = 'systemObject'
$allowAll
}


aclEntry principalType: 'user', principalName: '$userName', {
    systemObjectName = 'resources'
    objectType = 'systemObject'
$allowAll
}


aclEntry principalType: 'user', principalName: '$userName', {
    zoneName = 'default'
    objectType = 'zone'
$allowAll
}

"""
        login(userName, userName)
    }

    def switchAdmin() {
        def userName = System.getProperty("COMMANDER_USER", "admin")
        def password = System.getProperty("COMMANDER_PASSWORD", "changeme")
        login(userName, password)
    }

    def ignoreDepCache(ignore) {
        if (ignore) {
            dsl "setProperty '/plugins/$pluginName/project/__ignore_dependencies_cache', value: '1'"
        } else {
            try {
                dsl "deleteProperty '/plugins/$pluginName/project/__ignore_dependencies_cache'"
            } catch (Throwable e) {

            }
        }
    }

    def provisionSample(wait = false) {
        def gcp = buildGCP()
        def name = 'spec-instance-' + new Random().nextInt()
        def p = ProvisionInstanceParameters.builder()
            .instanceType('f1-micro')
            .sourceImage(gcp.getFromFamily('debian-cloud', 'debian-10'))
            .network('default')
            .subnetwork('default')
            .diskSizeGb(20)
            .instanceName(name)
            .build()
        def op = gcp.provisionInstance(p)
        if (wait) {
            gcp.blockUntilComplete(op, 30 * 1000)
        }
        return gcp.getInstance(name)
    }

}

