package io.zeebe.broker.logstreams.cfg;

import io.zeebe.broker.system.DirectoryConfiguration;

public class SnapshotStorageCfg extends DirectoryConfiguration
{
    @Override
    protected String componentDirectoryName()
    {
        return "snapshot";
    }
}
