package org.camunda.tngp.broker.workflow;

import org.camunda.tngp.broker.system.ComponentConfiguration;

public class WorkflowCfg extends ComponentConfiguration
{
    public int deploymentCacheSize = 32;

    public int payloadCacheSize = 64;
}
