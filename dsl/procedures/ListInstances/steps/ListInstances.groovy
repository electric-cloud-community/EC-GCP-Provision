$[/myProject/groovy/scripts/preamble.groovy.ignore]

GCPProvision plugin = new GCPProvision()
plugin.runStep('List Instances', 'List Instances', 'listInstances')