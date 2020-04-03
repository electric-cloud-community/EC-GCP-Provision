// This procedure.dsl was generated automatically
// DO NOT EDIT THIS BLOCK === procedure_autogen starts ===
procedure 'Start Instances', description: '''Starts one or more Virtual Machine Instances in GCP.''', {

    // Handling binary dependencies
    step 'flowpdk-setup', {
        description = "This step handles binary dependencies delivery"
        subprocedure = 'flowpdk-setup'
        actualParameter = [
            generateClasspathFromFolders: 'deps/libs'
        ]
    }

    step 'Start Instances', {
        description = ''
        command = new File(pluginDir, "dsl/procedures/StartInstances/steps/StartInstances.groovy").text
        shell = 'ec-groovy'
        shell = 'ec-groovy -cp $[/myJob/flowpdk_classpath]'

        resourceName = '$[flowpdkResource]'

        postProcessor = '''$[/myProject/perl/postpLoader]'''
    }

    formalOutputParameter 'instances',
        description: 'JSON representation of instances'
// DO NOT EDIT THIS BLOCK === procedure_autogen ends, checksum: 8321f67326639e1afca5360e93697ead ===
// Do not update the code above the line
// procedure properties declaration can be placed in here, like
// property 'property name', value: "value"
}