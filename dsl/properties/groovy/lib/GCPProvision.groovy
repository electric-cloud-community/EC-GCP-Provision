import com.cloudbees.flowpdf.*
import com.cloudbees.flow.plugins.*
import groovy.json.JsonParser
import groovy.json.JsonSlurper

/**
 * GCPProvision
 */
class GCPProvision extends FlowPlugin {

    @Override
    Map<String, Object> pluginInfo() {
        return [
            pluginName         : '@PLUGIN_KEY@',
            pluginVersion      : '@PLUGIN_VERSION@',
            configFields       : ['config'],
            configLocations    : ['ec_plugin_cfgs'],
            defaultConfigValues: [:]
        ]
    }


/**
 * provision - Provision/Provision
 * Add your code into this method and it will be called when the step runs
 * @param config (required: true)
 * @param instanceType (required: true)
 * @param sourceImage (required: true)
 * @param keys (required: false)
 * @param network (required: true)
 * @param subnetwork (required: true)
 * @param diskSize (required: false)
 * @param assignPublicIp (required: false)
 * @param count (required: false)
 * @param resourcePoolName (required: false)

 */
    def provision(StepParameters p, StepResult sr) {
        /* Log is automatically available from the parent class */
        log.info(
            "provision was invoked with StepParameters",
            /* runtimeParameters contains both configuration and procedure parameters */
            p.toString()
        )


        Map<String, String> param = p.asMap

        String instanceNameTemplate = p.getParameter('instanceNameTemplate')?.value
        if (!instanceNameTemplate) {
            instanceNameTemplate = param.resourcePoolName
        }
        def parameters = ProvisionInstanceParameters.builder()

        parameters.zoneName(param.zone)
        def region = param.zone.replaceAll(/\-\w$/, '')
        parameters.regionName(region)
        log.info "Using region $region"
        parameters.projectId(param.projectId)
        log.info "Using project $param.projectId"
        parameters.instanceType(param.instanceType)
        log.info "Using instance type ${param.instanceType}"

        if (param.diskSize) {
            parameters.diskSizeGb(Long.parseLong(param.diskSize))
            log.info "Using disk size $param.diskSize"
        }

        if (param.assignPublicIp == 'true') {
            parameters.assignPublicIp(true)
            log.info "Assigning a public IP"
        }
        parameters.sourceImage(param.sourceImage)
        log.info "Using source image $param.sourceImage"
        parameters.network(param.network)
        log.info "Using network $param.network"
        parameters.subnetwork(param.subnetwork)
        log.info "Using subnetwork $param.subnetwork"

        if (param.instanceTags) {
            List<String> tags = param.instanceTags.split(/\s*\n\s*/)
            log.info "Using tags $tags"
            parameters.tags(tags)
        }

        if (param.keys) {
            List mapKeys = new JsonSlurper().parseText(param.keys)
            List<ProvisionInstanceKey> keys = []
            for (Map k in mapKeys) {
                String userName = k.userName
                if (!userName) {
                    throw new RuntimeException("The key $k does not have a 'userName' field")
                }
                String keyString = k.key
                if (!keyString) {
                    throw new RuntimeException("The key $k does not have a 'key' field")
                }
                ProvisionInstanceKey key = ProvisionInstanceKey.builder().userName(userName).key(keyString).build()
                keys.add(key)
                log.info "Adding a key for $userName"
            }
            parameters.keys(keys)
        }

        int count = Integer.parseInt(param.count)
        def operations = []
        def names = []
        for (int i = 0; i < count; i++) {
            def name = generateInstanceName(instanceNameTemplate)
            def operation = gcp.provisionInstance(parameters.instanceName(name).build())
            names.add(name)
            log.info("Launched operation: ${operation.getName()}")
            //gcp.blockUntilComplete(operation, 60 * 1000)
            operations.add(operation)
        }
        operations.each {
            gcp.blockUntilComplete(it, 60 * 1000)
        }

        int port
        if (param.resourcePort) {
            port = Integer.parseInt(param.resourcePort)
        }
        else {
            port = 7800
        }

        def workspace = param.resourceWorkspace ?: 'default'
        def resourcePool = param.resourcePoolName
        if (resourcePool) {
            for(String name in names) {
                String ip = gcp.getInstanceInternalIp(name)
                log.info "Instance $name has IP $ip"
                //TODO external
                FlowAPI.ec.createResource(
                    resourceName: name,
                    resourcePools: resourcePool,
                    workspace: workspace,
                    port: port,
                    hostName: ip
                )
                FlowAPI.setFlowProperty("/resources/$name/ec_cloud_instance_details/createdBy", "@PLUGIN_KEY@")
                FlowAPI.setFlowProperty("/resources/$name/ec_cloud_instance_details/instance_id", name)
                FlowAPI.setFlowProperty("/resources/$name/ec_cloud_instance_details/config", param.config)
                log.info "Created resource $name in the pool $resourcePool"
            }
        }
    }

    private static generateInstanceName(String template) {
        String instanceName = template
            .toLowerCase()
            .replaceAll(/[\W]/, '')
            .replaceAll(/[\s_]/, '-') + '-' + Random.newInstance().nextInt()
        instanceName = instanceName.replaceAll(/-+/, '-')
        if (!(instanceName =~ /^[a-z]]/)) {
            instanceName = 'i-' + instanceName
        }
        return instanceName
    }

    @Lazy
    private GCP gcp = {
        Config config = context.getConfigValues()
        String key = config.getCredential('credential')?.secretValue
        if (!key) {
            throw new RuntimeException("The key is not found in the credential")
        }
        String projectId = config.getRequiredParameter('projectId').value
        String zone = config.getRequiredParameter('zone')
        GCP gcp = new GCP(key, projectId, zone)
        return gcp
    }()


/**
    * teardown - Teardown/Teardown
    * Add your code into this method and it will be called when the step runs
    * @param config (required: true)
    * @param resName (required: true)
    
    */
    def teardown(StepParameters p, StepResult sr) {

        /* Log is automatically available from the parent class */
        log.info(
          "teardown was invoked with StepParameters",
          /* runtimeParameters contains both configuration and procedure parameters */
          p.toString()
        )

        String resourceName = p.getRequiredParameter('resName').value

        String createdBy = FlowAPI.getFlowProperty("/resources/$resourceName/ec_cloud_instance_details/createdBy")
        log.info "The resource $resourceName is created by $createdBy"
        String instanceName = FlowAPI.getFlowProperty("/resources/$resourceName/ec_cloud_instance_details/instance_id")
        log.info "The instance id is $instanceName"
        String config = FlowAPI.getFlowProperty("/resources/$resourceName/ec_cloud_instance_details/config")
        log.info "The config name is $config"

        if (createdBy != '@PLUGIN_KEY@') {
            throw new RuntimeException("Cannot tear down the instance created by another plugin")
        }

        if (config == p.getRequiredParameter('config').value) {
            def operation = gcp.deleteInstance(instanceName)
            gcp.blockUntilComplete(operation, 30 * 1000)
        }
        else {
            throw new RuntimeException("Not implemented yet")
            // Launch procedure with the correct config
        }
    }

/**
    * deleteMachine - Delete Machine/Delete Machine
    * Add your code into this method and it will be called when the step runs
    * @param config (required: true)
    * @param instanceName (required: true)
    
    */
    def deleteMachine(StepParameters p, StepResult sr) {
        /* Log is automatically available from the parent class */
        log.info(
          "deleteMachine was invoked with StepParameters",
          /* runtimeParameters contains both configuration and procedure parameters */
          p.toString()
        )

        String instanceName = p.getRequiredParameter('instanceName').value
        def operation = gcp.deleteInstance(instanceName)
        log.info "Launched operation ${operation.getName()}"
        gcp.blockUntilComplete(operation, 30 * 1000)
    }

// === step ends ===

}