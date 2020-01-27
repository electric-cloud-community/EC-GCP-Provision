$[/myProject/groovy/scripts/preamble.groovy.ignore]

GCPProvision plugin = new GCPProvision()
plugin.runStep('Stop Instance', 'Stop Instance', 'stopInstance')