import com.cloudbees.flow.plugins.gcp.compute.DynamicDropdownHandler
import com.electriccloud.domain.FormalParameterOptionsResult

def result = new FormalParameterOptionsResult()

def parameterName = args.formalParameterName
def log = new File('/tmp/log.log')
log << args

DynamicDropdownHandler handler = DynamicDropdownHandler.getInstance(args)

//todo handle errors

if (handler) {
    switch (parameterName) {
        case 'instanceType':
            handler.listTypes().each {
                result.add(it.value, it.name)
            }
            break
        case 'network':
            handler.listNetworks().each {
                result.add(it.value, it.name)
            }
            break
        case 'subnetwork':
            String network = args.parameters?.network
            if (network) {
                handler.listSubnetworks(network).each {
                    result.add(it.value, it.name)
                }
            }
            break
        case 'sourceImageFamily':
            String project = args.parameters?.sourceImageProject
            handler.listFamilies(project).each {
                result.add(it.value, it.name)
            }
            break
    }
}
return result