package com.cloudbees.plugin.spec

import com.cloudbees.flow.plugins.gcp.compute.GCP
import com.cloudbees.flow.plugins.gcp.compute.GCPOptions
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
            [desc: "test configuration", checkConnection: "0",
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

    String getKey() {
        def token = System.getenv("GCP_KEY")
        assert token
        def parsed = new JsonSlurper().parseText(token)
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
        String params = actualParameters.collect {k, v -> "$k: '$v'"}.join(",")
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
        return new GCP(GCPOptions.builder().key(getKey()).zone(getZone()).build())
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

}

