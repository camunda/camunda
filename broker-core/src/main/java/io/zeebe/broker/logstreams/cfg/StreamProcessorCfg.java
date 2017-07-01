package io.zeebe.broker.logstreams.cfg;

import io.zeebe.broker.system.DirectoryConfiguration;

public class StreamProcessorCfg extends DirectoryConfiguration
{
    @Override
    protected String componentDirectoryName()
    {
        return "index";
    }
}
