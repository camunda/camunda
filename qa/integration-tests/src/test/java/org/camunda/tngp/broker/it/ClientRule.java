package org.camunda.tngp.broker.it;

import static org.camunda.tngp.logstreams.log.LogStream.*;

import java.io.IOException;
import java.util.Properties;
import java.util.function.Supplier;

import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.TopicClient;
import org.camunda.tngp.client.WorkflowTopicClient;
import org.camunda.tngp.client.impl.TngpClientImpl;
import org.camunda.tngp.transport.Channel;
import org.camunda.tngp.transport.impl.ChannelImpl;
import org.junit.rules.ExternalResource;

public class ClientRule extends ExternalResource
{

    protected final Properties properties;

    protected TngpClient client;

    public ClientRule()
    {
        this(Properties::new);
    }

    public ClientRule(final Supplier<Properties> propertiesProvider)
    {
        this.properties = propertiesProvider.get();

    }

    protected TngpClient createTngpClient()
    {
        return TngpClient.create(properties);
    }

    public void closeClient()
    {
        if (client != null)
        {
            client.close();
            client = null;
        }
    }

    @Override
    protected void before() throws Throwable
    {
        client = createTngpClient();
    }

    @Override
    protected void after()
    {
        closeClient();
    }

    public TngpClient getClient()
    {
        if (client == null)
        {
            client = createTngpClient();
        }

        return client;
    }

    public void interruptBrokerConnection()
    {
        final Channel channel = ((TngpClientImpl) client).getTransportManager().getChannelForCommand(null);
        try
        {
            ((ChannelImpl) channel).getSocketChannel().shutdownOutput();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public TopicClient topic()
    {
        return client.topic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);
    }

    public TaskTopicClient taskTopic()
    {
        return client.taskTopic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);
    }

    public WorkflowTopicClient workflowTopic()
    {
        return client.workflowTopic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);
    }

}
