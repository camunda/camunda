package io.zeebe.broker.clustering.management.config;

import io.zeebe.broker.system.DirectoryConfiguration;

public class ClusterManagementConfig extends DirectoryConfiguration
{
    @Override
    protected String componentDirectoryName()
    {
        return "meta";
    }
}
