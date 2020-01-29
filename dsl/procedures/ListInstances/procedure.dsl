// This procedure.dsl was generated automatically
// DO NOT EDIT THIS BLOCK === procedure_autogen starts ===
procedure 'List Instances', description: '''Lists instances for the provided parameters''', {

    // Handling binary dependencies
    step 'flowpdk-setup', {
        description = "This step handles binary dependencies delivery"
        subprocedure = 'flowpdk-setup'
        actualParameter = [
            generateClasspathFromFolders: 'deps/libs'
        ]
    }

    step 'List Instances', {
        description = ''
        command = new File(pluginDir, "dsl/procedures/ListInstances/steps/ListInstances.groovy").text
        shell = 'ec-groovy'
        shell = 'ec-groovy -cp $[/myJob/flowpdk_classpath]'

        resourceName = '$[flowpdkResource]'

        postProcessor = '''$[/myProject/perl/postpLoader]'''
    }

    formalOutputParameter 'instances',
        description: 'JSON representation of instances found'
// DO NOT EDIT THIS BLOCK === procedure_autogen ends, checksum: b4ca8c0393f9b372d112b472f6c46fee ===
// Do not update the code above the line
// procedure properties declaration can be placed in here, like
// property 'property name', value: "value"
}