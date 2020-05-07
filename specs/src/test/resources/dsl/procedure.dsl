def projName = args.projectName
def procName = args.procedureName
def params = args.params

project projName, {
    procedure procName, {

        step procName, {
            description = ''
            subprocedure = procName
            subproject = '/plugins/EC-GCP-Provision/project'

            params.each { name, defValue ->
                actualParameter name, '$[' + name + ']'
            }
        }

        params.each { name, defValue ->
            formalParameter name, defaultValue: defValue, {
                type = 'textarea'
            }
        }
    }
}
