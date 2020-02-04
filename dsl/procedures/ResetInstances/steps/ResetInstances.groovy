$[/myProject/groovy/scripts/preamble.groovy.ignore]

GCPProvision plugin = new GCPProvision()
plugin.runStep('Reset Instances', 'Reset Instances', 'resetInstances')