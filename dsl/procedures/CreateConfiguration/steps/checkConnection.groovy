import com.cloudbees.flow.plugins.GCP
import com.electriccloud.client.groovy.ElectricFlow

// Sample code
ElectricFlow ef = new ElectricFlow()
String projectId = ef.getProperty(propertyName: 'projectId')?.property?.value
String zone = ef.getProperty(propertyName: 'zone')?.property?.value
def credential = ef.getFullCredential(credentialName: 'credential')
println credential
def key = credential?.credential?.password
GCP gcp = new GCP(key, projectId, zone)
def list = gcp.compute.instances().list(projectId, zone).execute()
println list
