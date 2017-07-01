package io.zeebe.broker.workflow;

import io.zeebe.broker.system.ComponentConfiguration;

public class WorkflowCfg extends ComponentConfiguration
{
    public int deploymentCacheSize = 32;

    public int payloadCacheSize = 64;
}
