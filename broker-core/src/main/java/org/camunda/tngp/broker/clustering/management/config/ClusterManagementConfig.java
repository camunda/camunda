package org.camunda.tngp.broker.clustering.management.config;

import org.camunda.tngp.broker.system.DirectoryConfiguration;

public class ClusterManagementConfig extends DirectoryConfiguration
{
    @Override
    protected String componentDirectoryName()
    {
        return "meta";
    }
}
