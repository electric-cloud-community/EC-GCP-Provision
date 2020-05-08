package com.cloudbees.flow.plugins.gcp.compute

import com.cloudbees.flow.plugins.gcp.compute.logger.Logger
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.compute.ComputeScopes
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import groovy.json.JsonSlurper

class GCP {

    Compute compute
    String projectId
    String zone
    Logger log

    @Lazy
    String region = {zone.replaceAll(/\-\w$/, '')}()

    private String key

    public static final String APP_NAME = 'CloudBees Flow GCP Plugin'

    private static final String NETWORK_INTERFACE_CONFIG = "ONE_TO_ONE_NAT";
    private static final String NETWORK_ACCESS_CONFIG = "External NAT";
    private static final String SOURCE_IMAGE_PREFIX = "https://www.googleapis.com/compute/v1/projects/"
    private static final String SOURCE_IMAGE_PATH = "debian-cloud/global/images/debian-7-wheezy-v20150710"

    GCP(GCPOptions options) {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        //https://github.com/GoogleCloudPlatform/java-docs-samples/blob/master/compute/cmdline/src/main/java/ComputeEngineSample.java
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance()
        if (options.ignoreSsl) {
            //Because it will be eventually redefined to this
            httpTransport = new NetHttpTransport.Builder().doNotValidateCertificate().build()
        }


        String key = options.key
        assert key
        GoogleCredential credential = GoogleCredential.fromStream(
            new ByteArrayInputStream(key.getBytes('UTF-8')),
            httpTransport,
            jsonFactory
        )
        List<String> scopes = new ArrayList<>();
        // Set Google Cloud Storage scope to Full Control.
        scopes.add(ComputeScopes.DEVSTORAGE_FULL_CONTROL);
        // Set Google Compute Engine scope to Read-write.
        scopes.add(ComputeScopes.COMPUTE);
        credential = credential.createScoped(scopes);


        Compute compute = new Compute.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName(APP_NAME)
            .build()
        this.compute = compute
        assert options.zone
        this.zone = options.zone
        this.key = key
        if (options.projectId) {
            this.projectId = options.projectId
        }
        else {
            def parsed = new JsonSlurper().parseText(key)
            this.projectId = parsed.get("project_id")
        }
        assert this.projectId

        if (options.logger) {
            this.log = options.logger
        }
        else {
            //Void logger
            this.log = new Logger() {
                @Override
                void debug(Object... messages) {
                }

                @Override
                void info(Object... messages) {
                }

                @Override
                void trace(Object... messages) {
                }
            }
        }
    }


    Operation stopInstance(String instanceName) {
        Compute.Instances.Stop stop = compute.instances().stop(projectId, zone, instanceName)
        return stop.execute()
    }

    Operation startInstance(String instanceName) {
        compute.instances().start(projectId, zone, instanceName).execute()
    }

    Operation deleteInstance(String instanceName) {
        Compute.Instances.Delete delete = compute.instances().delete(projectId, zone, instanceName)
        return delete.execute()
    }

    Instance getInstance(String instanceName) {
        Instance instance = compute.instances().get(projectId, zone, instanceName).execute()
        return instance
    }


    Image getFromFamily(String project, String family) {
        Image image = compute.images().getFromFamily(project, family).execute()
        return image
    }

    def listInstances(ListInstancesParameters p) {
        Compute.Instances.List list = compute.instances().list(projectId, zone)
        if (p.filter) {
            list.setFilter(p.filter)
        }
        if (p.maxResults) {
            list.setMaxResults(p.maxResults)
        }
        if (p.orderBy) {
            list.setOrderBy(p.orderBy)
        }
        List<Instance> instances = list.execute().getItems()
        return instances
    }


    Operation provisionInstance(ProvisionInstanceParameters p) {
        String zone = p.zoneName ?: zone
        log.debug "Zone is $zone"

        Instance instance = new Instance()
        String instanceName = p.instanceName
        if (!instanceName) {
            throw new RuntimeException("instanceName is not provided")
        }
        instance.setName(p.instanceName)
        log.debug "Instance name is ${instance.getName()}"

        instance.setDescription(p.description ?: "Provisioned automatically by EC-GCP-Provision CloudBees Flow plugin")
        log.debug "Description: ${instance.getDescription()}"

        String instanceType = p.instanceType ?: 'n1-standard-1'
        instance.setMachineType(
            "https://www.googleapis.com/compute/v1/projects/"
                + projectId + "/zones/" + zone + "/machineTypes/" + instanceType)
        log.debug "Type: ${instance.getMachineType()}"

        NetworkInterface ifc = new NetworkInterface();
        String networkName = p.network ?: 'default'
        ifc.setNetwork("https://www.googleapis.com/compute/v1/projects/" +
            projectId + "/global/networks/${networkName}");
        List<AccessConfig> configs = new ArrayList<>()


        if (p.subnetwork) {
            String region = p.regionName ?: region
            if (!region) {
                throw new RuntimeException("Region is not provided")
            }
            ifc.setSubnetwork("projects/${projectId}/regions/${region}/subnetworks/${p.subnetwork}")
            log.debug "Subnetwork: ${ifc.getSubnetwork()}"
        }

        if (p.assignPublicIp) {
            //Public IP
            AccessConfig config = new AccessConfig()
            config.setType(NETWORK_INTERFACE_CONFIG)
            config.setName(NETWORK_ACCESS_CONFIG)
            configs.add(config)
        }
        if (p.deletionProtection) {
            instance.setDeletionProtection(true)
            log.debug "Set deletion protection"
        }

        ifc.setAccessConfigs(configs)
        instance.setNetworkInterfaces(Collections.singletonList(ifc))

        if (p.tags) {
            Tags tags = Tags.newInstance().setItems(p.tags)
            instance.setTags(tags)
            log.debug "Tag: $tags"
        }

        //// Initialize the service account to be used by the VM Instance and set the API access scopes.

        if (p.serviceAccountType in [ProvisionInstanceParameters.ServiceAccountType.DEFINED, ProvisionInstanceParameters.ServiceAccountType.SAME]) {
            ServiceAccount account = new ServiceAccount()
            String email = p.serviceAccountType == ProvisionInstanceParameters.ServiceAccountType.DEFINED ? p.serviceAccountEmail : getServiceAccountEmail()
            if (!email) {
                throw new RuntimeException("Service account email is not provided for the service account type ${p.serviceAccountType}")
            }
            account.setEmail(email)
            List<String> scopes = new ArrayList<>()
            scopes.add("https://www.googleapis.com/auth/devstorage.full_control")
            scopes.add("https://www.googleapis.com/auth/compute")
            account.setScopes(scopes)
            log.debug("Using service account $account")
            instance.setServiceAccounts(Collections.singletonList(account))
        }


        // Add attached Persistent Disk to be used by VM Instance.
        AttachedDisk disk = new AttachedDisk()
        disk.setBoot(true)
        disk.setAutoDelete(true)
        disk.setType("PERSISTENT")
        AttachedDiskInitializeParams params = new AttachedDiskInitializeParams()
        // Assign the Persistent Disk the same name as the VM Instance.
        params.setDiskName(p.instanceName);

        //compute.images().list(projectId).execute().getItems().first().getFamily()
        // Specify the source operating system machine image to be used by the VM Instance.
        if (p.sourceImage) {
            params.setSourceImage(p.sourceImage.getSelfLink())

        } else {
            String sourceImage = p.sourceImageUrl ?: SOURCE_IMAGE_PATH
            params.setSourceImage(SOURCE_IMAGE_PREFIX + sourceImage)
        }
        log.debug "Set source image ${params.getSourceImage()}"
        // Specify the disk type as Standard Persistent Disk
        params.setDiskType("https://www.googleapis.com/compute/v1/projects/"
            + projectId + "/zones/"
            + zone + "/diskTypes/pd-standard")
        if (p.diskSizeGb) {
            params.setDiskSizeGb(p.diskSizeGb)
        }

        log.debug "Disk params: ${params}"

        disk.setInitializeParams(params)
        instance.setDisks(Collections.singletonList(disk))

        //Adding ssh keys
        Metadata metadata = Metadata.newInstance()
        List<Metadata.Items> items = []
        if (p.keys) {
            def keyStrings = []
            for (ProvisionInstanceKey key in p.keys) {
                String keyString = "$key.userName:$key.key"
                keyStrings << keyString
            }
            Metadata.Items item = new Metadata.Items();
            item.setKey('ssh-keys')
            item.setValue(keyStrings.join("\n"))
            items << item
        }

        log.debug "Metadata: ${metadata}"
        if (p.hostname) {
            instance.setHostname(p.hostname)
        }
        metadata.setItems(items)
        instance.setMetadata(metadata)

        log.debug "Instance: $instance"

        Compute.Instances.Insert insert = compute.instances().insert(projectId, zone, instance);
        Operation operation = insert.execute()
        return operation
    }


    String getInstanceInternalIp(String instanceName) {
        Instance instance = compute.instances().get(projectId, zone, instanceName).execute()
        String ip = instance.getNetworkInterfaces().first().getNetworkIP()
        return ip
    }

    String getInstanceExternalIp(String instanceName) {
        Instance instance = compute.instances().get(projectId, zone, instanceName).execute()
        String externalIp = instance.getNetworkInterfaces().first().getAccessConfigs()?.first()?.getNatIP()
        return externalIp
    }


    List<MachineType> listTypes() {
        MachineTypeList machineTypeList = compute.machineTypes().list(projectId, zone).execute()
        return machineTypeList.getItems()
    }

    List<Network> listNetworks() {
        return compute.networks().list(projectId).execute().getItems()
    }

    List<Subnetwork> listSubnetworks() {
        return compute.subnetworks().list(projectId, region).execute().getItems()
    }

    List<Image> listImages(String project) {
        return compute.images().list(project ?: this.projectId).execute().getItems()
    }

    Tuple createImage(CreateImageParameters p) {
        Image image = new Image()
        if (p.sourceImage) {
            image.setSourceImage(p.sourceImage)
            log.info "Using source image ${image.getSourceImage()}"
        } else if (p.sourceDisk) {
            log.info "Zone is $p.zone"
            if (!p.zone) {
                log.info "Using zone from the configuration ${zone}"
            }
            String zoneName = p.zone ? p.zone : this.zone
            image.setSourceDisk("/zones/$zoneName/disks/$p.sourceDisk")
            log.info "Using image source disk ${image.getSourceDisk()}"
        } else if (p.sourceSnapshot) {
            image.setSourceSnapshot(p.sourceSnapshot)
            log.info "Using image source snapshot ${image.getSourceSnapshot()}"
        } else {
            throw new RuntimeException("Either source image, snapshot or disk must be provided")
        }

        if (p.locations) {
            image.setStorageLocations(p.locations)
            log.info "Using locations ${p.locations}"
        }
        if (p.family) {
            image.setFamily(p.family)
            log.info "Using image family ${image.getFamily()}"
        }
        image.setDescription(p.description)
        if (p.diskSizeGb) {
            image.setDiskSizeGb(p.diskSizeGb)
            log.info "Using image disk size ${image.getDiskSizeGb()}"
        }

        if (p.name) {
            image.setName(p.name)
        } else if (p.family) {
            String dateSuffix = new Date().format("yyyyMMdd")
            List<Image> family = compute.images().list(projectId).setFilter("family = ${p.family}").execute().getItems()
            String name = p.family + '-' + dateSuffix
            List<Image> duplicates = family.findAll {
                it.getName().startsWith(name)
            }
            int counter = duplicates.size()
            log.info "Found $counter images in the family for the same date"
            if (counter > 0) {
                name += "-" + counter
            }
            image.setName(name)
        } else {
            throw new RuntimeException("Either image name or a family name must be provided")
        }
        log.info("Using image name ${image.getName()}")

        Operation insert = compute.images().insert(projectId, image).setForceCreate(p.forceCreate).execute()
        return new Tuple(insert, image)
    }

    Operation deprecateImage(String name, String replacement) {
        DeprecationStatus deprecationStatus = new DeprecationStatus()
        deprecationStatus.setReplacement(replacement)
        deprecationStatus.setState("DEPRECATED")
        Operation deprecate = compute.images().deprecate(projectId, name, deprecationStatus).execute()
        return deprecate
    }

    Operation resetInstance(String name) {
        return compute.instances().reset(projectId, zone, name).execute()
    }

    void blockUntilComplete(Operation operation, long timeout) {
        long start = System.currentTimeMillis();
        final long pollInterval = 5 * 1000;
        String zone = operation.getZone();  // null for global/regional operations
        if (zone != null) {
            String[] bits = zone.split("/");
            zone = bits[bits.length - 1];
        }
        String status = operation.getStatus();
        String opId = operation.getName();
        while (operation != null && !status.equals("DONE")) {
            Thread.sleep(pollInterval)
            log.info("Waiting...")
            long elapsed = System.currentTimeMillis() - start;
            if (timeout > 0 && elapsed >= timeout) {
                throw new InterruptedException("Timed out waiting for operation to complete");
            }
            if (zone != null) {
                Compute.ZoneOperations.Get get = compute.zoneOperations().get(projectId, zone, opId);
                operation = get.execute();
            } else {
                Compute.GlobalOperations.Get get = compute.globalOperations().get(projectId, opId);
                operation = get.execute();
            }
            if (operation != null) {
                status = operation.getStatus();
            }
        }
        if (operation != null) {
            def error = operation.getError()
            if (error) {
                String messages = ""
                error.getErrors().each {
                    println it.getMessage()
                    messages += it.getMessage() + "\n"
                }
                throw new RuntimeException(messages)
            }
        }
    }


    private String getServiceAccountEmail() {
        Map parsed = new JsonSlurper().parseText(this.key)
        String email = parsed.client_email
        assert email
        return email
    }
}

