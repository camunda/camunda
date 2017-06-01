package org.camunda.tngp.client.util;

import static org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_PARTITION_ID;
import static org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_TOPIC_NAME;

import java.util.Properties;
import java.util.function.Supplier;

import org.camunda.tngp.client.TaskTopicClient;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.TopicClient;
import org.camunda.tngp.client.WorkflowTopicClient;
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

    protected TngpClient createClient()
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
        client = createClient();
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
            client = createClient();
        }

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

}
