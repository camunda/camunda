package org.camunda.tngp.broker.protocol.clientapi;

import java.io.InputStream;
import java.util.function.Supplier;

import org.agrona.LangUtil;
import org.camunda.tngp.broker.Broker;
import org.junit.rules.ExternalResource;

public class EmbeddedBrokerRule extends ExternalResource
{
    protected Broker broker;

    protected Supplier<InputStream> configSupplier;

    public EmbeddedBrokerRule()
    {
        this(() -> null);
    }

    public EmbeddedBrokerRule(Supplier<InputStream> configSupplier)
    {
        this.configSupplier = configSupplier;
    }

    public EmbeddedBrokerRule(String configFileClasspathLocation)
    {
        this(() -> EmbeddedBrokerRule.class.getClassLoader().getResourceAsStream(configFileClasspathLocation));
    }

    @Override
    protected void before() throws Throwable
    {
        startBroker();
    }

    @Override
    protected void after()
    {
        stopBroker();
    }

    public void restartBroker()
    {
        stopBroker();
        startBroker();
    }

    protected void stopBroker()
    {
        broker.close();
    }

    protected void startBroker()
    {
        broker = new Broker(configSupplier.get());
        try
        {
            Thread.sleep(1000);
        }
        catch (InterruptedException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

}
