package org.camunda.tngp.broker;

import java.io.InputStream;

import org.camunda.tngp.broker.log.LogComponent;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.broker.system.metrics.MetricsComponent;
import org.camunda.tngp.broker.system.threads.ThreadingComponent;
import org.camunda.tngp.broker.taskqueue.TaskQueueComponent;
import org.camunda.tngp.broker.transport.TransportComponent;
import org.camunda.tngp.broker.wf.WfComponent;

public class Broker implements AutoCloseable
{
    protected final SystemContext brokerContext;

    public Broker(String configFileLocation)
    {
        System.out.println("Starting broker");

        brokerContext = new SystemContext(configFileLocation);

        start();

        System.out.println("Broker started.");
    }

    public Broker(InputStream configStream)
    {
        System.out.println("Starting broker");

        brokerContext = new SystemContext(configStream);

        start();

        System.out.println("Broker started.");
    }

    protected void start()
    {
        brokerContext.addComponent(new MetricsComponent());
        brokerContext.addComponent(new ThreadingComponent());
        brokerContext.addComponent(new TransportComponent());
        brokerContext.addComponent(new LogComponent());
        brokerContext.addComponent(new TaskQueueComponent());
        brokerContext.addComponent(new WfComponent());

        brokerContext.init();
    }

    @Override
    public void close()
    {
        System.out.println("Closing broker");
        brokerContext.close();
        System.out.println("Broker closed.");
    }
}
