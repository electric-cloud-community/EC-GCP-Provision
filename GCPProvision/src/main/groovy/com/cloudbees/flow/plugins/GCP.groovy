package com.cloudbees.flow.plugins

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.compute.ComputeScopes
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.AccessConfig
import com.google.api.services.compute.model.AttachedDisk
import com.google.api.services.compute.model.AttachedDiskInitializeParams
import com.google.api.services.compute.model.ImageList
import com.google.api.services.compute.model.Instance
import com.google.api.services.compute.model.MachineTypeList
import com.google.api.services.compute.model.NetworkList
import com.google.api.services.compute.model.Operation
import com.google.api.services.compute.model.ServiceAccount
import com.google.api.services.compute.model.Tags
import com.google.api.services.compute.model.Metadata
import com.google.api.services.compute.model.NetworkInterface
import com.google.auth.http.HttpTransportFactory

import java.lang.reflect.Method


class GCP {

    Compute compute
    String projectId
    String zone

    public static final String APP_NAME = 'CloudBees Flow GCP Plugin'

    private static final String NETWORK_INTERFACE_CONFIG = "ONE_TO_ONE_NAT";
    private static final String NETWORK_ACCESS_CONFIG = "External NAT";
    private static final String SOURCE_IMAGE_PREFIX = "https://www.googleapis.com/compute/v1/projects/";
    private static final String SOURCE_IMAGE_PATH = "debian-cloud/global/images/debian-7-wheezy-v20150710";


    GCP(String key, String projectId, String zone, boolean ignoreSsl = false) {
        GoogleCredential credential = GoogleCredential.fromStream(new ByteArrayInputStream(key.getBytes('UTF-8')))
        List<String> scopes = new ArrayList<>();
        // Set Google Cloud Storage scope to Full Control.
        scopes.add(ComputeScopes.DEVSTORAGE_FULL_CONTROL);
        // Set Google Compute Engine scope to Read-write.
        scopes.add(ComputeScopes.COMPUTE);
        credential = credential.createScoped(scopes);

        //https://github.com/GoogleCloudPlatform/java-docs-samples/blob/master/compute/cmdline/src/main/java/ComputeEngineSample.java
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        if (ignoreSsl) {
            println "Ignoring SSL certificates validation"
            httpTransport = new NetHttpTransport.Builder().doNotValidateCertificate().build()
        }
        Compute compute = new Compute.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName(APP_NAME)
            .build()

        this.compute = compute
        this.zone = zone
        this.projectId = projectId
    }


    Operation stopInstance(String instanceName) {
        Compute.Instances.Stop stop = compute.instances().stop(projectId, zone, instanceName)
        return stop.execute()

    }

    Operation deleteInstance(String instanceName) {
        Compute.Instances.Delete delete = compute.instances().delete(projectId, zone, instanceName)
        return delete.execute()
    }

    Instance getInstance(String instanceName) {
        Instance instance = compute.instances().get(projectId, zone, instanceName).execute()
        return instance
    }

    Operation provisionInstance(ProvisionInstanceParameters p) {
        Instance instance = new Instance()
        String instanceName = p.instanceName
        if (!instanceName) {
            throw new RuntimeException("instanceName uis not provided")
        }
        instance.setName(p.instanceName)
        String instanceType = p.instanceType ?: 'n1-standard-1'
        instance.setMachineType(
            "https://www.googleapis.com/compute/v1/projects/"
                + p.projectId + "/zones/" + p.zoneName + "/machineTypes/" + instanceType)



        NetworkInterface ifc = new NetworkInterface();
        String networkName = p.network ?: 'default'
        ifc.setNetwork("https://www.googleapis.com/compute/v1/projects/" +
            p.projectId + "/global/networks/${networkName}");
        List<AccessConfig> configs = new ArrayList<>()
        if (p.subnetwork) {
            ifc.setSubnetwork("projects/${p.projectId}/regions/${p.regionName}/subnetworks/${p.subnetwork}")
        }

        if (p.assignPublicIp) {
            //Public IP
            AccessConfig config = new AccessConfig()
            config.setType(NETWORK_INTERFACE_CONFIG);
            config.setName(NETWORK_ACCESS_CONFIG);
            configs.add(config);
        }

        ifc.setAccessConfigs(configs)
        instance.setNetworkInterfaces(Collections.singletonList(ifc))

        if (p.tags) {
            Tags tags = Tags.newInstance().setItems(p.tags)
            instance.setTags(tags)
        }

        //// Initialize the service account to be used by the VM Instance and set the API access scopes.
        ServiceAccount account = new ServiceAccount()
        account.setEmail("default")
        List<String> scopes = new ArrayList<>()
        scopes.add("https://www.googleapis.com/auth/devstorage.full_control")
        scopes.add("https://www.googleapis.com/auth/compute")
        account.setScopes(scopes)
        instance.setServiceAccounts(Collections.singletonList(account))


        // Add attached Persistent Disk to be used by VM Instance.
        AttachedDisk disk = new AttachedDisk()
        disk.setBoot(true)
        disk.setAutoDelete(true)
        disk.setType("PERSISTENT")
        AttachedDiskInitializeParams params = new AttachedDiskInitializeParams()
        // Assign the Persistent Disk the same name as the VM Instance.
        params.setDiskName(p.instanceName);

        // Specify the source operating system machine image to be used by the VM Instance.
        String sourceImage = p.sourceImage ?: SOURCE_IMAGE_PATH
        params.setSourceImage(SOURCE_IMAGE_PREFIX + sourceImage)

        // Specify the disk type as Standard Persistent Disk
        params.setDiskType("https://www.googleapis.com/compute/v1/projects/"
            + p.projectId + "/zones/"
            + p.zoneName + "/diskTypes/pd-standard")
        if (p.diskSizeGb) {
            params.setDiskSizeGb(p.diskSizeGb)
        }

        disk.setInitializeParams(params);
        instance.setDisks(Collections.singletonList(disk))

        //Adding ssh keys
        Metadata metadata = Metadata.newInstance()
        List<Metadata.Items> items = []
        if (p.keys) {
            for(ProvisionInstanceKey key in p.keys) {
                Metadata.Items item = new Metadata.Items();
                item.setKey('ssh-keys')
                item.setValue("${key.userName}:${key.key}")
                items << item
            }
        }
        metadata.setItems(items)
        instance.setMetadata(metadata)

        Compute.Instances.Insert insert = compute.instances().insert(p.projectId, p.zoneName, instance);
        Operation operation = insert.execute()
        return operation
    }


    void listImages() {
        ImageList list = compute.images().list(projectId).execute()
        list.getItems().each {
            println it.getName()
            println it.getSourceImage()
            println it.getSourceImageId()
            //TODO images from shared projects
        }
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


    List<Map> listTypes() {
        MachineTypeList machineTypeList = compute.machineTypes().list(projectId, zone).execute()
        List<Map> types = []
        machineTypeList.getItems().each {
            types.add([name: it.getName(), value: it.getName()])

        }
        return types
    }

    void listNetworks() {
        NetworkList networkList = compute.networks().list(projectId).execute()
        networkList.getItems().each {
            println it.getName()
            println it.getSubnetworks()
        }
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
            Thread.sleep(pollInterval);
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed >= timeout) {
                throw new InterruptedException("Timed out waiting for operation to complete");
            }
            println "Waiting...."
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
}

