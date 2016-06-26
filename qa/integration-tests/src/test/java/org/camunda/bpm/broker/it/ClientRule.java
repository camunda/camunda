package org.camunda.bpm.broker.it;

import java.util.Properties;

import org.camunda.tngp.client.TngpClient;
import org.junit.rules.ExternalResource;

public class ClientRule extends ExternalResource
{
    protected final Properties properties;

    protected TngpClient client;

    public ClientRule()
    {
        this(new Properties());
    }

    public ClientRule(Properties properties)
    {
        this.properties = properties;

    }

    @Override
    protected void before() throws Throwable
    {
        client = TngpClient.create(properties);
        client.connect();
    }

    @Override
    protected void after()
    {
        client.close();
    }

    public TngpClient getClient()
    {
        return client;
    }

}
