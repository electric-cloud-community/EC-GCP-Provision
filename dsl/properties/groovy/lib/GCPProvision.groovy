import com.cloudbees.flowpdf.*
import com.cloudbees.flow.plugins.*
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.electriccloud.client.groovy.models.ActualParameter


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
            for (String name in names) {
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

        def details = [:]
        String resultProperty = param.resultProperty
        for (String name in names) {
            def instance = gcp.getInstance(name)
            String internalIp = gcp.getInstanceInternalIp(name)
            String externalIp = gcp.getInstanceExternalIp(name) ?: ""
            details.put(name, [
                internalIp: internalIp,
                externalIp: externalIp,
                link: instance.getSelfLink()
            ])
            if (names.size() == 1) {
                FlowAPI.setFlowProperty("$resultProperty/instanceName", name)
                FlowAPI.setFlowProperty("$resultProperty/internalIp", internalIp)
                FlowAPI.setFlowProperty("$resultProperty/externalIp", externalIp)
                if (resourcePool) {
                    FlowAPI.setFlowProperty("$resultProperty/resourceName", name)
                }
            }
            else {
                FlowAPI.setFlowProperty("$resultProperty/$name/instanceName", name)
                FlowAPI.setFlowProperty("$resultProperty/$name/internalIp", internalIp)
                FlowAPI.setFlowProperty("$resultProperty/$name/externalIp", externalIp)
                if (resourcePool) {
                    FlowAPI.setFlowProperty("$resultProperty/$name/resourceName", name)
                }
            }
        }
        FlowAPI.setFlowProperty("$resultProperty/json", JsonOutput.toJson(details))
        sr.setOutputParameter('instanceDetails', JsonOutput.toJson(details))
        sr.apply()
    }

    private static generateInstanceName(String template) {
        String instanceName = template
            .toLowerCase()
            .replaceAll(/[^a-z0-9]+/, '-') + '-' + Random.newInstance().nextInt()
        instanceName = instanceName.replaceAll(/-+/, '-')
        if (!(instanceName =~ /^[a-z]/)) {
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
        GCP gcp = new GCP(key, projectId, zone, false)
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

        def resourcePool
        def resources = []
        try {
            resourcePool = FlowAPI.ec.getResourcePool(resourcePoolName: resourceName)
            log.info "Resource Pool: $resourcePool"
            resourcePool?.resourcePool?.resourceNames?.resourceName?.each {
                resources << it
            }
        } catch (Throwable e) {
            log.info "Failed to get resource pool $resourceName"
            log.info "${e.message}"
        }

        try {
            def resource = FlowAPI.ec.getResource(resourceName: resourceName)
            resources << resourceName
        }
        catch(Throwable e) {
            log.info "Failed to get resource $resourceName"
            log.info("${e.message}")
            return
        }

        def configs = [:]
        boolean hasErrors = false
        for(String resName in resources) {
            String createdBy = FlowAPI.getFlowProperty("/resources/$resName/ec_cloud_instance_details/createdBy")
            log.info "The resource $resName is created by $createdBy"
            String instanceName = FlowAPI.getFlowProperty("/resources/$resName/ec_cloud_instance_details/instance_id")
            log.info "The instance id is $instanceName"
            String config = FlowAPI.getFlowProperty("/resources/$resName/ec_cloud_instance_details/config")
            log.info "The config name is $config"
            if (createdBy == '@PLUGIN_KEY@') {
                //def instances = configs.get(config, [])
                //instances << instanceName
                //configs.put(config, instances)

                def result = FlowAPI.ec.runProcedure(
                    procedureName: 'Delete Machine',
                    projectName: '/plugins/@PLUGIN_KEY@/project',
                    actualParameters: [
                        new ActualParameter('config', config),
                        new ActualParameter('instanceName', instanceName),
                    ]
                )
                def jobId = result?.jobId
                pollJob(jobId)
                def status = FlowAPI.ec.getJobStatus(jobId: jobId)
                def outcome = status?.outcome
                if (outcome != 'error') {
                    //Delete resource
                    log.info "Job $jobId completed"
                    FlowAPI.ec.deleteResource(resourceName: resName)
                    log.info "Deleted resource $resName"
                }
                else {
                    //fail
                    hasErrors = true
                    log.info "Failed to delete resource $resName: job $jobId has failed"
                }
            }
        }

    }

    void pollJob(String jobId) {
        log.info "Polling job $jobId"
        def status = FlowAPI.ec.getJobStatus(jobId: jobId)?.status
        while(status != 'completed') {
            sleep(2 * 1000)
            log.info "Polling job..."
            status = FlowAPI.ec.getJobStatus(jobId: jobId)?.status
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
        def instance
        try {
            instance = gcp.getInstance(instanceName)
            log.info "Instance: $instance"
        }
        catch (Throwable e) {
            log.info "$e.message"
            log.info "Cannot get instance $instanceName: probably already deleted"
            return
        }

        def operation = gcp.deleteInstance(instanceName)
        log.info "Launched operation ${operation.getName()}"
        gcp.blockUntilComplete(operation, 60 * 1000)
    }

// === step ends ===

}