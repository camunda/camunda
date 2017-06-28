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
import org.camunda.tngp.transport.impl.ChannelImpl;
import org.camunda.tngp.transport.impl.ChannelManagerImpl;
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

    public void interruptBrokerConnections()
    {
        final ChannelManagerImpl channelManager = (ChannelManagerImpl) ((TngpClientImpl) client).getChannelManager();

        for (final ChannelImpl channel : channelManager.getManagedChannels())
        {
            try
            {
                channel.getSocketChannel().shutdownOutput();
            }
            catch (final IOException e)
            {
                throw new RuntimeException(e);
            }
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
