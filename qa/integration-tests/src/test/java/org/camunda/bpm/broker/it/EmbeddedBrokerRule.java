package org.camunda.bpm.broker.it;

import java.io.InputStream;
import java.util.function.Supplier;

import org.camunda.tngp.broker.Broker;
import org.junit.rules.ExternalResource;

public class EmbeddedBrokerRule extends ExternalResource
{
    protected Broker broker;

    protected Supplier<InputStream> configSupplier;

    public EmbeddedBrokerRule()
    {
        this(null);
    }

    public EmbeddedBrokerRule(Supplier<InputStream> configSupplier)
    {
        this.configSupplier = configSupplier;
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
    }

}
