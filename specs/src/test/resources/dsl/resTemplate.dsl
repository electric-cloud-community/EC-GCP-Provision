def projName = args.projName
def config = args.config
def name = args.name
def envTemplateName = args.envTemplateName

project projName, {

    resourceTemplate name, {
        description = ''
        cfgMgrPluginKey = null
        cfgMgrProcedure = null
        cfgMgrProjectName = null
        cloudProviderParameter = [
            'config'              : config,
            'count'               : '1',
            'instanceNameTemplate': 'test-instance',
            'instanceType'        : 'f1-micro',
            'network'             : 'default',
            'pingResource'        : 'false',
            'resourcePort'        : '7800',
            'resourceWorkspace'   : 'default',
            'resultProperty'      : '/myJob/result',
            'sourceImageFamily'   : 'debian-10',
            'sourceImageProject'  : 'debian-cloud',
            'subnetwork'          : 'default',
            'useServiceAccount'   : 'noAccount',
            'waitTimeout'         : '300',
        ]
        cloudProviderPluginKey = 'EC-GCP-Provision'
        cloudProviderProcedure = 'Provision'
        cloudProviderProjectName = null
        projectName = projName

        // Custom properties

        property 'ec_cloud_plugin_parameter', {

            // Custom properties
            config = 'args.config'
            count = '1'
            instanceNameTemplate = 'test-instance'
            instanceType = 'f1-micro'
            network = 'default'
            pingResource = 'false'
            resourcePort = '7800'
            resourceWorkspace = 'default'
            resultProperty = '/myJob/result'
            sourceImageFamily = 'debian-10'
            sourceImageProject = 'debian-cloud'
            subnetwork = 'default'
            useServiceAccount = 'noAccount'
            waitTimeout = '300'
        }

        property 'ec_deploy', {

            // Custom properties

            property 'ec_usageCount', value: '3', {
                description = 'A count of how many times this Resource Template has been used to spin up Resource Pools\"'
                expandable = '1'
                suppressValueTracking = '1'
            }
        }
    }


    environmentTemplate envTemplateName, {
        projectName = projName

        environmentTemplateTier 'Tier 1', {
            resourceCount = '1'
            resourceTemplateName = name
            resourceTemplateProjectName = projName
        }

        // Custom properties

        property 'ec_deploy', {

            // Custom properties

            property 'ec_usageCount', value: '0', {
                description = 'A count of how many times this Environment Template has been used to spin up Environments'
                expandable = '1'
                suppressValueTracking = '1'
            }
        }
    }
}