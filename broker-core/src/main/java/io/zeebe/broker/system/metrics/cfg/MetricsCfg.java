package io.zeebe.broker.system.metrics.cfg;

import io.zeebe.broker.system.DirectoryConfiguration;

public class MetricsCfg extends DirectoryConfiguration
{
    @Override
    protected String componentDirectoryName()
    {
        return "metrics";
    }
}
