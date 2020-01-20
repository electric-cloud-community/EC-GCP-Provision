//Replace this line with your content


import com.cloudbees.flow.plugins.*
import com.electriccloud.domain.FormalParameterOptionsResult

def result = new FormalParameterOptionsResult()

println args
def parameterName = args.formalParameterName
def config = args.configurationParameters
def projectId = config.projectId
def zone = config.zone
def key = args.credential[0].password


def parameters = args.parameters
new File('/tmp/log.log').write(args.toString())

GCP gcp = new GCP(key, projectId, zone, true)

if (parameterName == 'instanceType') {
    List<Map> types = gcp.listTypes()
    types.each {
        result.add(it.name, it.value)
    }
}

return result