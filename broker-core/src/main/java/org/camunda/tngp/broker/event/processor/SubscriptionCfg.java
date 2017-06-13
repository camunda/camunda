package org.camunda.tngp.broker.event.processor;

import org.camunda.tngp.broker.system.DirectoryConfiguration;

public class SubscriptionCfg extends DirectoryConfiguration
{
    @Override
    protected String componentDirectoryName()
    {
        return "subscription";
    }
}
