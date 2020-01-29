$[/myProject/groovy/scripts/preamble.groovy.ignore]

GCPProvision plugin = new GCPProvision()
plugin.runStep('Delete Instances', 'Delete Instances', 'deleteInstances')