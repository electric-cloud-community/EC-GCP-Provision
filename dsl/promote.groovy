
// DO NOT EDIT THIS BLOCK === promote_autogen starts ===
import groovy.transform.BaseScript
import com.electriccloud.commander.dsl.util.BasePlugin

//noinspection GroovyUnusedAssignment
@BaseScript BasePlugin baseScript

// Variables available for use in DSL code
def pluginName = args.pluginName
def upgradeAction = args.upgradeAction
def otherPluginName = args.otherPluginName

def pluginKey = getProject("/plugins/$pluginName/project").pluginKey
def pluginDir = getProperty("/projects/$pluginName/pluginDir").value

//List of procedure steps to which the plugin configuration credentials need to be attached
def stepsWithAttachedCredentials = [
    [procedureName: "Delete Machine", stepName: "Delete Machine"],
    [procedureName: "Run Script", stepName: "Run Script"],
    [procedureName: "Stop Instance", stepName: "Stop Instance"],
    [procedureName: "Start Instance", stepName: "Start Instance"],
    [procedureName: "Provision", stepName: "Provision"],

]

project pluginName, {
    property 'ec_keepFilesExtensions', value: 'true'
    property 'ec_formXmlCompliant', value: 'true'
    loadPluginProperties(pluginDir, pluginName)
    loadProcedures(pluginDir, pluginKey, pluginName, stepsWithAttachedCredentials)

    // Plugin configuration metadata
    property 'ec_config', {
        configLocation = 'ec_plugin_cfgs'
        form = '$[' + "/projects/$pluginName/procedures/CreateConfiguration/ec_parameterForm]"
        property 'fields', {
            property 'desc', {
                property 'label', value: 'Description'
                property 'order', value: '1'
            }
        }
    }

    // Properties
    property 'ec_dsl_libraries_path', {

value = 'agent/deps/libs'

}

    property 'ec_configurations', {

value = 'ec_plugin_cfgs'

}

    property 'ec_cloudprovisioning_plugin', {

property 'configurationLocation', {

value = 'ec_plugin_cfgs'

}

property 'displayName', {

value = 'GCP'

}

property 'hasConfiguration', {

value = '1'

}

property 'operations', {

property 'createConfiguration', {

property 'procedureName', {

value = 'CreateConfiguration'

}

property 'ui_formRefs', {

property 'parameterForm', {

value = 'procedures/CreateConfiguration/ec_parameterForm'

}

}

property 'parameterRefs', {

property 'configuration', {

value = 'config'

}

}

}

property 'deleteConfiguration', {

property 'procedureName', {

value = 'DeleteConfiguration'

}

property 'ui_formRefs', {

property 'parameterForm', {

value = 'procedures/DeleteConfiguration/ec_parameterForm'

}

}

property 'parameterRefs', {

property 'configuration', {

value = 'config'

}

}

}

property 'retireResource', {

property 'procedureName', {

value = 'Teardown'

}

property 'parameterRefs', {

property 'resourceName', {

value = 'resName'

}

}

}

property 'retireResourcePool', {

property 'procedureName', {

value = 'Teardown'

}

property 'parameterRefs', {

property 'resourcePoolName', {

value = 'resName'

}

property 'configuration', {

value = 'config'

}

}

}

property 'provision', {

property 'procedureName', {

value = 'Provision'

}

property 'ui_formRefs', {

property 'parameterForm', {

value = 'ec_parameterForm'

}

}

property 'parameterRefs', {

property 'configuration', {

value = 'config'

}

property 'count', {

value = 'count'

}

property 'resourcePool', {

value = 'resourcePoolName'

}

}

}

}

}

    }

def retainedProperties = []

upgrade(upgradeAction, pluginName, otherPluginName, stepsWithAttachedCredentials, 'ec_plugin_cfgs', retainedProperties)
// DO NOT EDIT THIS BLOCK === promote_autogen ends, checksum: d7738f14441cf183ad0faae582f71f31 ===
// Do not edit the code above this line

project pluginName, {
    // You may add your own DSL instructions below this line, like
    // property 'myprop', {
    //     value: 'value'
    // }
}