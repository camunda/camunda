package io.zeebe.broker.event.processor;

import io.zeebe.broker.system.DirectoryConfiguration;

public class SubscriptionCfg extends DirectoryConfiguration
{
    @Override
    protected String componentDirectoryName()
    {
        return "subscription";
    }
}
