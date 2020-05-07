package com.cloudbees.flow.plugins.gcp.compute

import spock.lang.Shared


class ProvisionInstanceTest extends SpecHelper {
    @Shared
    GCP gcp
    @Shared
    String url
    @Shared
    String type = 'f1-micro'
    @Shared
    ProvisionInstanceParameters genericParameters
    @Shared
    String instanceName

    def setupSpec() {
        gcp = buildGCP()
    }

    def setup() {
        instanceName = randomize('test-simple-instance')
        genericParameters = ProvisionInstanceParameters.builder()
            .instanceType(type)
            .sourceImage(gcp.getFromFamily('debian-cloud', 'debian-10'))
            .network('default')
            .subnetwork('default')
            .diskSizeGb(20)
            .instanceName(instanceName)
            .build()
    }

    def cleanup() {
        gcp.deleteInstance(instanceName)
    }

    def 'provision instance'() {
        when:
        def operation = gcp.provisionInstance(genericParameters)
        then:
        assert operation.getName()
        def instance = gcp.getInstance(instanceName)
        assert instance.getDisks().size() == 1
    }

    def 'provision instance with two keys'() {
        setup:
        def key1 = 'ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQCwQKibIYqBk8G2994+jRc+lmh2KKNp1zfjJK4BXEpoD7D5ie0WNpE3KxVA+R8YGUVIQXwo1ZgPKRSGSolr9PE0OBXYHKcgzYyPeXgtMPQvnAYYkrdaMqVQmclJkpLA6nHHmM4ObswFB+t9NS7XQnGYGwI05dakaCA4NiYpwUAQb9GR0WfpZY495em0NSihn8+JjDEtPKAkNyNDk9ur+w+3w+S8Lv0xc/xdS6GgJmNJr9SZaLTrxAlB0FVCHrwo3CBFCDpFSQZYA44ZxMB7K8FsFfcv73k7DlF7jB+nQinNFx8MPec0oaiVvMBtVtPMiC43O/lvhlDQEYP1Z6qPk8NUvtoYqdqllJDAOJ29XyHFjS9gQnKYa7fevAMflRJs2aQLfHpdoiM+S+RLvdzCHgKdTK+qnZeDeWnnmTBVfA9pdKl3LBFEwseV8PyhAJG0WiW9WFKJKe0w7BOMhwO+n27t1PnKz9yOfDRLJFKhYYcXZoE91QXTqDKCzGEfg9Mpyis= imago@polinas-macbook'
        def key2 = 'ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQDadhsAnIllvhWQxGwtOhx57My9hklCd3xWJyV+krPL6wBEU7nznel/hF3P/WcbOPYbOj4MojHlOudv3mujFDIPdFU0okRe4OpmmiJMWLV1306M7x0yMI3uKelEhXiOQEk4Y/pzCf7vgpDUO15o4uDjwyqSdXHvsIcRHQ6Nec5DbPpoioiJADOr/IK4p3iIpURSKP9DTAm+XanC5+rKsnls2UZuZ3pX01QwNGCFWZPjruwOYaK8TzoMagF1w8w3ojo2Y5Fnuspdw7KSzkgXwYvV/xru4NP55NTxKyJvWNUyWG00juGWv6Gm0mx6DM7Ioxy9cTUq2O8uAqzqXOy+1/37 imago@imagos-MacBook.local'
        genericParameters.keys = [
            ProvisionInstanceKey.builder().key(key1).userName('imago').build(),
            ProvisionInstanceKey.builder().key(key2).userName('imago').build(),
        ]
        when:
        def operation = gcp.provisionInstance(genericParameters)
        then:
        def instance = gcp.getInstance(instanceName)
        def keys =  instance.getMetadata().getItems().find { it.getKey() == 'ssh-keys' }
        assert keys == "imago:$key1\nimago:$key2"
    }

    def 'provision without public ip'() {
        setup:
        genericParameters.assignPublicIp = false
        when:
        def operation = gcp.provisionInstance(genericParameters)
        gcp.blockUntilComplete(operation, 50 * 1000)
        then:
        def instance = gcp.getInstance(instanceName)
        def ip = gcp.getInstanceExternalIp(instanceName)
        assert ip == null
    }

    def 'provision with public ip'() {
        setup:
        genericParameters.assignPublicIp = true
        when:
        def operation = gcp.provisionInstance(genericParameters)
        gcp.blockUntilComplete(operation, 50 * 1000)
        then:
        def instance = gcp.getInstance(instanceName)
        def ip = gcp.getInstanceExternalIp(instanceName)
        assert ip
    }
}
