package org.camunda.tngp.broker.logstreams.cfg;

import org.camunda.tngp.broker.system.DirectoryConfiguration;

public class SnapshotStorageCfg extends DirectoryConfiguration
{
    @Override
    protected String componentDirectoryName()
    {
        return "snapshot";
    }
}
