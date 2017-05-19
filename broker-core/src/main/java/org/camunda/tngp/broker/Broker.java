package org.camunda.tngp.broker;

import java.io.InputStream;

import org.camunda.tngp.broker.clustering.ClusterComponent;
import org.camunda.tngp.broker.logstreams.LogStreamsComponent;
import org.camunda.tngp.broker.system.SystemComponent;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.broker.task.TaskQueueComponent;
import org.camunda.tngp.broker.transport.TransportComponent;
import org.camunda.tngp.broker.workflow.WorkflowComponent;

public class Broker implements AutoCloseable
{
    protected final SystemContext brokerContext;

    public Broker(String configFileLocation)
    {
        brokerContext = new SystemContext(configFileLocation);
        start();
    }

    public Broker(InputStream configStream)
    {
        brokerContext = new SystemContext(configStream);
        start();
    }

    protected void start()
    {
        brokerContext.addComponent(new SystemComponent());
        brokerContext.addComponent(new TransportComponent());
        brokerContext.addComponent(new LogStreamsComponent());
        brokerContext.addComponent(new TaskQueueComponent());
        brokerContext.addComponent(new WorkflowComponent());
        brokerContext.addComponent(new ClusterComponent());

        brokerContext.init();
    }

    @Override
    public void close()
    {
        brokerContext.close();
        System.out.println("Broker closed.");
    }

    public SystemContext getBrokerContext()
    {
        return brokerContext;
    }
}
