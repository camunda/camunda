package io.zeebe.broker.it;

import static io.zeebe.logstreams.log.LogStream.*;

import java.io.IOException;
import java.util.Properties;
import java.util.function.Supplier;

import io.zeebe.client.TaskTopicClient;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.TopicClient;
import io.zeebe.client.WorkflowTopicClient;
import io.zeebe.client.impl.ZeebeClientImpl;
import io.zeebe.transport.impl.ChannelImpl;
import io.zeebe.transport.impl.ChannelManagerImpl;
import org.junit.rules.ExternalResource;

public class ClientRule extends ExternalResource
{

    protected final Properties properties;

    protected ZeebeClient client;

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
        client = ZeebeClient.create(properties);
        client.connect();
    }

    @Override
    protected void after()
    {
        client.close();
    }

    public ZeebeClient getClient()
    {
        return client;
    }

    public void interruptBrokerConnections()
    {
        final ChannelManagerImpl channelManager = (ChannelManagerImpl) ((ZeebeClientImpl) client).getChannelManager();

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
