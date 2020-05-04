// This procedure.dsl was generated automatically
// DO NOT EDIT THIS BLOCK === procedure_autogen starts ===
procedure 'Run Script', description: '''Runs a custom groovy script using the GCP client.''', {

    // Handling binary dependencies
    step 'flowpdk-setup', {
        description = "This step handles binary dependencies delivery"
        subprocedure = 'flowpdk-setup'
        actualParameter = [
            generateClasspathFromFolders: 'deps/libs'
        ]
    }

    step 'Run Script', {
        description = ''
        command = new File(pluginDir, "dsl/procedures/RunScript/steps/RunScript.groovy").text
        shell = 'ec-groovy'
        shell = 'ec-groovy -cp $[/myJob/flowpdk_classpath]'

        resourceName = '$[/myJobStep/parent/steps/flowpdk-setup/flowpdkResource]'

        postProcessor = '''$[/myProject/perl/postpLoader]'''
    }

    formalOutputParameter 'output',
        description: '''Some output parameter to use in the script. Evaluation result (the last value returned by the script) will be saved into this parameter.'''
// DO NOT EDIT THIS BLOCK === procedure_autogen ends, checksum: 132053ee7272826ca8600dfeb9bf19ec ===
// Do not update the code above the line
// procedure properties declaration can be placed in here, like
// property 'property name', value: "value"
}