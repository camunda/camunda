package io.zeebe.client.util;

import static io.zeebe.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_PARTITION_ID;
import static io.zeebe.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_TOPIC_NAME;

import java.util.Properties;
import java.util.function.Supplier;

import io.zeebe.client.TaskTopicClient;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.TopicClient;
import io.zeebe.client.WorkflowTopicClient;
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

    public String getDefaultTopicName()
    {
        return DEFAULT_TOPIC_NAME;
    }

    public int getDefaultPartitionId()
    {
        return DEFAULT_PARTITION_ID;
    }

}
