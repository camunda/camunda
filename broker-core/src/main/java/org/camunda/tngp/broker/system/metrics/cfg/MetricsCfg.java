package org.camunda.tngp.broker.system.metrics.cfg;

import org.camunda.tngp.broker.system.DirectoryConfiguration;

public class MetricsCfg extends DirectoryConfiguration
{
    @Override
    protected String componentDirectoryName()
    {
        return "metrics";
    }
}
