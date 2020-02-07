import com.cloudbees.flowpdf.*
import com.cloudbees.flow.plugins.*
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import com.electriccloud.client.groovy.models.ActualParameter
import org.codehaus.groovy.control.CompilerConfiguration

import java.text.SimpleDateFormat


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

        for (String key : param.keySet()) {
            if (param.get(key) instanceof String) {
                param.put(key, param.get(key).trim())
            }
        }

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

        if (param.sourceImage) {
            parameters.sourceImageUrl(param.sourceImage)
        } else if (param.sourceImageFamily) {
            String family = param.sourceImageFamily
            String project = param.sourceImageProject ?: param.projectId
            def image = gcp.getFromFamily(project, family)
            log.info "Fetched image ${image.getName()} (${image.getSelfLink()})"
            parameters.sourceImage(image)
        } else {
            throw new RuntimeException("Either source image URL or family should be provided")
        }

        log.info "Using source image $param.sourceImage"
        parameters.network(param.network)
        log.info "Using network $param.network"
        parameters.subnetwork(param.subnetwork)
        log.info "Using subnetwork $param.subnetwork"


        if (param.instanceTags) {
            List<String> tags = param.instanceTags.split(/\s*\n+\s*/)
            log.info "Using tags $tags"
            parameters.tags(tags)
        }

        parameters.hostname(param.instanceHostname)


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
        parameters.deletionProtection(param.deletionProtection == "true")

        int count = Integer.parseInt(param.count)
        def operations = []
        def names = []
        for (int i = 0; i < count; i++) {
            def name = generateInstanceName(instanceNameTemplate)
            def operation = gcp.provisionInstance(parameters.instanceName(name).build())
            names.add(name)
            log.info("Launched operation: ${operation.getName()}")
            operations.add(operation)
        }
        int timeout = p.getParameter('waitTimeout')?.value as int ?: 300
        operations.each {
            gcp.blockUntilComplete(it, timeout * 1000)
        }

        int port
        if (param.resourcePort) {
            port = Integer.parseInt(param.resourcePort)
        } else {
            port = 7800
        }

        def workspace = param.resourceWorkspace ?: 'default'
        def resourcePool = param.resourcePoolName

        def zone = param.resourceZone ?: 'default'
        if (resourcePool) {
            for (String name in names) {
                String ip = gcp.getInstanceInternalIp(name)
                log.info "Instance $name has IP $ip"
                FlowAPI.ec.createResource(
                    resourceName: name,
                    description: 'GCP provisioned resource (dynamic)',
                    resourcePools: resourcePool,
                    workspaceName: workspace,
                    port: port,
                    hostName: ip,
                    zoneName: zone
                )
                log.info "Created resource $name in zone $zone, pool $resourcePool"
                String me = '$[/myJob/launchedByUser]'

                try {
                    FlowAPI.ec.createAclEntry(
                        principalType: 'user',
                        principalName: "project: @PLUGIN_NAME@",
                        modifyPrivilege: 'allow',
                        readPrivilege: 'allow',
                        changePermissionPrivilege: 'allow',
                        executePrivilege: 'allow',
                        resourceName: name
                    )
                }
                catch (Throwable e) {
                    log.info("Failed to grant ACL for the principal project: @PLUGIN_NAME@ at the resource: ${e.message}")
                }
                try {


                    FlowAPI.ec.createAclEntry(
                        principalType: 'user',
                        principalName: me,
                        modifyPrivilege: 'allow',
                        readPrivilege: 'allow',
                        changePermissionPrivilege: 'allow',
                        executePrivilege: 'allow',
                        resourceName: name
                    )
                } catch (Throwable e) {
                    log.info "Failed to grant ACL for $me: ${e.message}"
                }
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
            log.info "Instance $name has internal IP $internalIp"
            String externalIp = gcp.getInstanceExternalIp(name) ?: ""
            if (externalIp) {
                log.info "Instance $name has external IP $externalIp"
            }
            details.put(name, [
                internalIp: internalIp,
                externalIp: externalIp,
                link      : instance.getSelfLink()
            ])
            if (names.size() == 1) {
                FlowAPI.setFlowProperty("$resultProperty/instanceName", name)
                FlowAPI.setFlowProperty("$resultProperty/internalIp", internalIp)
                FlowAPI.setFlowProperty("$resultProperty/externalIp", externalIp)
                if (resourcePool) {
                    FlowAPI.setFlowProperty("$resultProperty/resourceName", name)
                }
            } else {
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
        catch (Throwable e) {
            log.info "Failed to get resource $resourceName"
            log.info("${e.message}")
            return
        }

        def configs = [:]
        boolean hasErrors = false
        for (String resName in resources) {
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
                    procedureName: 'Delete Instances',
                    projectName: '/plugins/@PLUGIN_KEY@/project',
                    actualParameters: [
                        new ActualParameter('config', config),
                        new ActualParameter('instanceNames', instanceName),
                        new ActualParameter('timeoutSeconds', '300'),
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
                } else {
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
        while (status != 'completed') {
            sleep(2 * 1000)
            log.info "Polling job..."
            status = FlowAPI.ec.getJobStatus(jobId: jobId)?.status
        }
    }

/**
 * runScript - Run Script/Run Script
 * Add your code into this method and it will be called when the step runs
 * @param config (required: true)
 * @param script (required: )

 */
    def runScript(StepParameters p, StepResult sr) {

        /* Log is automatically available from the parent class */
        log.info(
            "runScript was invoked with StepParameters",
            /* runtimeParameters contains both configuration and procedure parameters */
            p.toString()
        )
        String script = p.getRequiredParameter('script').value
        def compute = gcp.compute
        def compilerConfiguration = new CompilerConfiguration()
        compilerConfiguration.scriptBaseClass = DelegatingScript.class.name
        def shell = new GroovyShell(this.class.classLoader, new Binding([compute: compute]), compilerConfiguration)
        def gcpScript = new GCPScript(compute, gcp.projectId, gcp.zone, FlowAPI.ec, gcp)
        Script s = shell.parse(script)
        s.setDelegate(gcpScript)
        def result = s.run()
        log.info "Script evaluation result: $result"
        if (result) {
            sr.setOutputParameter('output', JsonOutput.toJson(result))
            sr.setJobStepSummary(new JsonBuilder(result).toPrettyString())
        }
    }

/**
 * stopInstance - Stop Instance/Stop Instance
 * Add your code into this method and it will be called when the step runs
 * @param config (required: true)
 * @param instanceName (required: true)

 */
    def stopInstance(StepParameters p, StepResult sr) {

        /* Log is automatically available from the parent class */
        log.info(
            "stopInstance was invoked with StepParameters",
            /* runtimeParameters contains both configuration and procedure parameters */
            p.toString()
        )

        String name = p.getRequiredParameter('instanceName').value
        def operation = gcp.stopInstance(name)
        gcp.blockUntilComplete(operation, 60 * 1000)
    }


/**
 * listInstances - List Instances/List Instances
 * Add your code into this method and it will be called when the step runs
 * @param config (required: true)
 * @param filter (required: false)
 * @param maxResults (required: )
 * @param orderBy (required: false)
 * @param resultProperty (required: true)

 */
    def listInstances(StepParameters p, StepResult sr) {

        /* Log is automatically available from the parent class */
        log.info(
            "listInstances was invoked with StepParameters",
            /* runtimeParameters contains both configuration and procedure parameters */
            p.toString()
        )

        def param = ListInstancesParameters.builder()
        Map pMap = p.asMap

        if (pMap.filter) {
            param.filter(pMap.filter)
        }
        if (pMap.orderBy) {
            param.orderBy(pMap.orderBy)
        }
        if (pMap.maxResults) {
            param.maxResults(Long.parseLong(pMap.maxResults))
        }
        def instances = gcp.listInstances(param.build())
        String result = p.getRequiredParameter('resultProperty').value

        def json = JsonOutput.toJson(instances)
        def names = []
        for (def instance in instances) {
            String name = instance.getName()
            FlowAPI.setFlowProperty("$result/$name/createdAt", instance.getCreationTimestamp())
            FlowAPI.setFlowProperty("$result/$name/status", instance.getStatus())
            FlowAPI.setFlowProperty("$result/$name/deletionProtection", instance.getDeletionProtection().toString())

            log.info "=================================="
            log.info "Found instance $name"
            log.info "Created at: ${instance.getCreationTimestamp()}"
            log.info "Status: ${instance.getStatus()}"
            log.info "Deletion protection: ${instance.getDeletionProtection()}"

            names << name
        }
        FlowAPI.setFlowProperty("$result/names", names.join(", "))
        FlowAPI.setFlowProperty("$result/json", json)
        log.info "Instances data: " + new JsonBuilder(instances).toPrettyString()
        sr.setOutputParameter("instances", json)
    }

/**
 * deleteInstances - Delete Machines/Delete Machines
 * Add your code into this method and it will be called when the step runs
 * @param config (required: true)
 * @param instanceNames (required: true)
 * @param timeoutSeconds (required: )

 */
    def deleteInstances(StepParameters p, StepResult sr) {

        /* Log is automatically available from the parent class */
        log.info(
            "deleteMachines was invoked with StepParameters",
            /* runtimeParameters contains both configuration and procedure parameters */
            p.toString()
        )

        String instanceNames = p.getRequiredParameter('instanceNames').value
        int timeout = p.getRequiredParameter('timeoutSeconds').value as int

        def names = instanceNames.split(/\n+/)
        def operations = []
        names.each { name ->
            try {
                def instance = gcp.getInstance(name)
                log.info "Found instance ${instance.getName()}"
                def operation = gcp.deleteInstance(name)
                operations << operation
                log.info "Launched operation ${operation.getName()}"
            } catch (Throwable e) {
                log.warning e.getMessage()
                log.warning e.getClass().getName()
                log.info("Cannot get instance $name: probably already deleted")
            }
        }

        operations.each { op ->
            gcp.blockUntilComplete(op, timeout * 1000)
        }
    }

/**
 * cleanupInstances - Cleanup Instances/Cleanup Instances
 * Add your code into this method and it will be called when the step runs
 * @param config (required: true)
 * @param filter (required: )
 * @param age (required: )
 * @param dryRun (required: )

 */
    def cleanupInstances(StepParameters p, StepResult sr) {

        /* Log is automatically available from the parent class */
        log.info(
            "cleanupInstances was invoked with StepParameters",
            /* runtimeParameters contains both configuration and procedure parameters */
            p.toString()
        )

        //2020-01-28T07:14:14.593-08:00",
        Date now = new Date()
        long nowTime = now.getTime()
        int hours = p.asMap.age as int
        def instances = gcp.listInstances(ListInstancesParameters.builder().filter(p.asMap.filter).build())
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        def oeprations = []
        for (def instance in instances) {
            if (instance.getDeletionProtection()) {
                log.info "Instance ${instance.getName()} is protected from deletion"
                continue
            }
            Date creationDate = format.parse(instance.getCreationTimestamp())
            long age = nowTime - creationDate.getTime()
            long ageHours = age / (1000 * 60 * 60)
            if (ageHours > hours) {
                log.info "Found instance ${instance.getName()} of age $ageHours hours"
            }
        }
    }

/**
 * resetInstances - Reset Instances/Reset Instances
 * Add your code into this method and it will be called when the step runs
 * @param config (required: true)
 * @param instanceNames (required: true)

 */
    def resetInstances(StepParameters p, StepResult sr) {

        /* Log is automatically available from the parent class */
        log.info(
            "resetInstances was invoked with StepParameters",
            /* runtimeParameters contains both configuration and procedure parameters */
            p.toString()
        )

        String instanceNames = p.getRequiredParameter('instanceNames').value
        def operations = instanceNames.split(/\s*\n+\s*/).collect { name ->
            log.info "Instance $name is going to reset"
            gcp.resetInstance(name.trim())
        }
        operations.each {
            log.info "Waiting for the operation ${it.getName()}"
            gcp.blockUntilComplete(it, 300 * 1000)
            log.info "Operation ${it.getName()} has completed"
        }
    }

/**
 * stopInstances - Stop Instances/Stop Instances
 * Add your code into this method and it will be called when the step runs
 * @param config (required: true)
 * @param instanceNames (required: true)

 */
    def stopInstances(StepParameters p, StepResult sr) {

        /* Log is automatically available from the parent class */
        log.info(
            "stopInstances was invoked with StepParameters",
            /* runtimeParameters contains both configuration and procedure parameters */
            p.toString()
        )

        String names = p.getRequiredParameter('instanceNames').value
        def operations = []
        def instanceNames = []
        names.split(/[\s\n]+/).each {
            def operation = gcp.stopInstance(it.trim())
            log.info "Launched operation ${operation.getName()}"
            operations << operation
            instanceNames << it.trim()
        }
        operations.each {
            gcp.blockUntilComplete(it, 300 * 1000)
        }
    }

/**
 * startInstances - Start Instances/Start Instances
 * Add your code into this method and it will be called when the step runs
 * @param config (required: true)
 * @param instanceNames (required: true)
 * @param resultProperty (required: true)

 */
    def startInstances(StepParameters p, StepResult sr) {
        /* Log is automatically available from the parent class */
        log.info(
            "startInstances was invoked with StepParameters",
            /* runtimeParameters contains both configuration and procedure parameters */
            p.toString()
        )


        String names = p.getRequiredParameter('instanceNames').value
        def operations = []
        def instanceNames = []
        names.split(/[\s\n]+/).each {
            def operation = gcp.startInstance(it.trim())
            log.info "Launched operation ${operation.getName()}"
            operations << operation
            instanceNames << it.trim()
        }
        operations.each {
            gcp.blockUntilComplete(it, 300 * 1000)
        }

        def instances = instanceNames.collect {
            gcp.getInstance(it)
        }
        sr.setOutputParameter('instances', JsonOutput.toJson(instances))
        sr.apply()
        String prettyJson = new JsonBuilder(instances).toPrettyString()
        log.info "Instances: $prettyJson"
        String resultProperty = p.getRequiredParameter('resultProperty').value
        FlowAPI.setFlowProperty("$resultProperty/json", prettyJson)
    }

// === step ends ===

    String getRuntimeLink() {
        String jobId = System.getenv('COMMANDER_JOBID')
        def link = "/commander/link/jobDetails/$jobId"
        return 'http://$[/server/webServerHost]' + link
    }

}


class GCPScript {
    def compute
    def project
    def zone
    def ef
    def wrapper

    GCPScript(compute, project, zone, ef, wrapper) {
        this.compute = compute
        this.project = project
        this.zone = zone
        this.ef = ef
        this.wrapper = wrapper
    }

}