package com.cloudbees.flow.plugins.gcp.compute

class DynamicDropdownHandler {
    GCP gcp

    private DynamicDropdownHandler(GCP gcp) {
        this.gcp = gcp
    }

    static getInstance(def args) {
        def config = args.get('configurationParameters')
        if (!config) {
            return null
        }
        String projectId = config?.projectId
        String zone = config?.zone
        String key = args.credential?.getAt(0)?.password
        if (!key) {
            return null
        }

        return new DynamicDropdownHandler(new GCP(
            GCPOptions.builder()
                .projectId(projectId)
                .zone(zone)
                .ignoreSsl(true)
                .key(key)
                .build()
        ))
    }


    List<SelectOption> listTypes() {
        return gcp.listTypes().findAll {
            !it.getDeprecated()?.getDeprecated()
        }.sort { a, b ->
            a.getGuestCpus() <=> b.getGuestCpus() ?: a.getMemoryMb() <=> b.getMemoryMb()
        }.collect {
            new SelectOption(name: it.getName() + ' (' + it.getDescription() + ')', value: it.getName())
        }
    }

    List<SelectOption> listNetworks() {
        gcp.listNetworks().collect {
            new SelectOption(value: it.getName(), name: it.getName())
        }
    }

    List<SelectOption> listSubnetworks(String network) {
        gcp.listSubnetworks().findAll {
            it.getNetwork().endsWith(network)
        }.collect {
            new SelectOption(name: it.getName(), value: it.getName())
        }
    }

    List<SelectOption> listFamilies(String project) {
        gcp.listImages(project).collect {
            it.getFamily()
        }.findAll { it }.unique().collect {
            new SelectOption(name: it, value: it)
        }
    }

    class SelectOption {
        String name
        String value
    }
}
