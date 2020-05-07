import com.cloudbees.flow.plugins.gcp.compute.GCP
import com.cloudbees.flow.plugins.gcp.compute.GCPOptions
import com.electriccloud.client.groovy.ElectricFlow

ElectricFlow ef = new ElectricFlow()
String projectId = ef.getProperty(propertyName: 'projectId')?.property?.value
String zone = ef.getProperty(propertyName: 'zone')?.property?.value
def credential = ef.getFullCredential(credentialName: 'credential')
def key = credential?.credential?.password
GCP gcp = new GCP(GCPOptions.builder().key(key).zone(zone).projectId(projectId).build())
try {
    def list = gcp.compute.instances().list(projectId, zone).execute()
} catch(Throwable e) {
    ef.setProperty(propertyName: "/myJob/configError", value: "Failed to connect to GCP: ${e.message}")
}
