package FlowPlugin::GCPProvision;
use strict;
use warnings;
use base qw/FlowPDF/;
use FlowPDF::Log;

# Feel free to use new libraries here, e.g. use File::Temp;

# Service function that is being used to set some metadata for a plugin.
sub pluginInfo {
    return {
        pluginName          => '@PLUGIN_KEY@',
        pluginVersion       => '@PLUGIN_VERSION@',
        configFields        => ['config'],
        configLocations     => ['ec_plugin_cfgs'],
        defaultConfigValues => {}
    };
}

# Auto-generated method for the procedure Scale Pool/Scale Pool
# Add your code into this method and it will be called when step runs
# $self - reference to the plugin object
# $p - step parameters
# $sr - StepResult object
sub scalePool {
    my ($self, $p, $sr) = @_;

    my $context = $self->getContext();
    logInfo("Current context is: ", $context->getRunContext());
    logInfo("Step parameters are: ", $p);

    my $configValues = $context->getConfigValues();
    logInfo("Config values are: ", $configValues);

    $sr->setJobStepOutcome('warning');
    $sr->setJobSummary("This is a job summary.");


    my $ec = ElectricCommander->new;
    my $resources = $ec->getResources({poolName => ''});

    my $load = calculatePoolLoad($resources);

}


sub calculatePoolLoad {
    # Check for the running jobs
    # Check for the waiting jobs

    # if there are waiting jobs scale pool up to the number of waiting jobs but no more than limit
    # if there are no waiting jobs but all the resources are taken check for the load in the part hour

    # scaling type?? pre-scale
    # if pre-scale is set, then add few resources just in case few == 20 percent from the current load

    # 5 is running + 1 reosurce
    # percent is taken from the parameters

    # Downscale:
    # If there are empty resources, check the history
    # If no jobs for a period of time (default 30 minutes)
    # scale down

    # 1. scale to the number of running jobs no less than minimal number
    # 2. scale to the number of running jobs + pre-scale calculation (to hold something just in case)

    # Take resources for downscale: take ones that are not used in the past
}
## === step ends ===
# Please do not remove the marker above, it is used to place new procedures into this file.


1;