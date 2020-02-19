my @objTypes = ('projects', 'resources', 'workspaces');
my $query    = $commander->newBatch();
my @reqs     = map { $query->getAclEntry('user', "project: $pluginName", { systemObjectName => $_ }) } @objTypes;
push @reqs, $query->getProperty('/server/ec_hooks/promote');
$query->submit();

foreach my $type (@objTypes) {
    if ($query->findvalue(shift @reqs, 'code') ne 'NoSuchAclEntry') {
        $batch->deleteAclEntry('user', "project: $pluginName", { systemObjectName => $type });
    }
}

if ($promoteAction eq 'promote') {
    foreach my $type (@objTypes) {
        $batch->createAclEntry(
            'user',
            "project: $pluginName",
            {
                systemObjectName           => $type,
                readPrivilege              => 'allow',
                modifyPrivilege            => 'allow',
                executePrivilege           => 'allow',
                changePermissionsPrivilege => 'allow'
            }
        );
    }
}
