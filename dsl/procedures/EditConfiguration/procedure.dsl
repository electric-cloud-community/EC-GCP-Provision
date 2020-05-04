
// DO NOT EDIT THIS BLOCK === configuration starts ===
// This part is auto-generated and will be regenerated upon subsequent updates
procedure 'EditConfiguration', description: 'Checks connection for the changed configuration', {

    //First, let's download third-party dependencies
    step 'flowpdk-setup', {
        description = "This step handles binary dependencies delivery"
        subprocedure = 'flowpdk-setup'
        actualParameter = [
            generateClasspathFromFolders: 'deps/libs'
        ]
        resourceName = 'local'
    }

    step 'checkConnection',
        command: new File(pluginDir, "dsl/procedures/CreateConfiguration/steps/checkConnection.groovy").text,
        errorHandling: 'abortProcedure',
        shell: 'ec-groovy -cp $[/myJob/flowpdk_classpath]',
        postProcessor: '$[/myProject/perl/postpLoader]',
        resourceName: 'local',
        condition: '$[/javascript myJob.checkConnection == "true" || myJob.checkConnection == "1"]'

}
// DO NOT EDIT THIS BLOCK === configuration ends, checksum: a87427b04cff5e61480bc6cae5d6a33b ===
