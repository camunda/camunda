package org.camunda.bpm.broker.it;

import org.camunda.tngp.broker.Broker;
import org.junit.rules.ExternalResource;

public class EmbeddedBrokerRule extends ExternalResource
{
    protected Broker broker;

    @Override
    protected void before() throws Throwable
    {
        broker = new Broker(null);
    }

    @Override
    protected void after()
    {
        broker.close();
    }

}
