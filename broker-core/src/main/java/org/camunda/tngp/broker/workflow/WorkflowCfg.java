package org.camunda.tngp.broker.workflow;

import org.camunda.tngp.broker.system.ComponentConfiguration;

public class WorkflowCfg extends ComponentConfiguration
{
    public int cacheSize = 512;
    public int maxPayloadSize = 1024 * 2;
}
