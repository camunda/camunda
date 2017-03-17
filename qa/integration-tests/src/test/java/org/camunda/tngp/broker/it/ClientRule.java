package org.camunda.tngp.broker.it;

import java.util.Properties;
import java.util.function.Supplier;

import org.camunda.tngp.client.TngpClient;
import org.junit.rules.ExternalResource;

public class ClientRule extends ExternalResource
{
    protected final Properties properties;

    protected TngpClient client;

    public ClientRule()
    {
        this(() -> new Properties());
    }

    public ClientRule(Supplier<Properties> propertiesProvider)
    {
        this.properties = propertiesProvider.get();

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
