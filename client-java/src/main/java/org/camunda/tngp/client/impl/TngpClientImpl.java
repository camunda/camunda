package org.camunda.tngp.client.impl;

import static org.camunda.tngp.client.ClientProperties.*;
import static org.camunda.tngp.util.EnsureUtil.*;

import java.util.Properties;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.tngp.client.ClientProperties;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.WorkflowTopicClient;
import org.camunda.tngp.client.event.impl.TopicClientImpl;
import org.camunda.tngp.client.task.impl.subscription.SubscriptionManager;
import org.camunda.tngp.transport.SocketAddress;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class TngpClientImpl implements TngpClient
{

    protected final Properties initializationProperties;

    protected final TransportManager transportManager;
    protected final SubscriptionManager subscriptionManager;
    protected final ClientCmdExecutor cmdExecutor;
    protected final ObjectMapper objectMapper;


    public TngpClientImpl(final Properties properties)
    {
        ClientProperties.setDefaults(properties);
        this.initializationProperties = properties;

        final String brokerContactPoint = properties.getProperty(BROKER_CONTACTPOINT);

        final SocketAddress contactPoint = SocketAddress.from(brokerContactPoint);

        transportManager =
            TransportManager.create()
                .maxConnections(properties.getProperty(CLIENT_MAXCONNECTIONS))
                .maxRequests(properties.getProperty(CLIENT_MAXREQUESTS))
                .maxMessageSize(1024 * 1024)
                .sendBufferSize(properties.getProperty(CLIENT_SENDBUFFER_SIZE))
                .threadingMode(properties.getProperty(CLIENT_THREADINGMODE))
                .keepAlivePeriod(properties.getProperty(CLIENT_TCP_CHANNEL_KEEP_ALIVE_PERIOD))
                .build();

        transportManager.registerBroker(contactPoint);

        cmdExecutor = new ClientCmdExecutor(transportManager);

        objectMapper = new ObjectMapper(new MessagePackFactory());
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        final int numExecutionThreads = Integer.parseInt(properties.getProperty(CLIENT_TASK_EXECUTION_THREADS));
        final Boolean autoCompleteTasks = Boolean.parseBoolean(properties.getProperty(CLIENT_TASK_EXECUTION_AUTOCOMPLETE));

        final int prefetchCapacity = Integer.parseInt(properties.getProperty(ClientProperties.CLIENT_TOPIC_SUBSCRIPTION_PREFETCH_CAPACITY));
        subscriptionManager = new SubscriptionManager(
                this,
                numExecutionThreads,
                autoCompleteTasks,
                prefetchCapacity,
                transportManager.openSubscription("task-acquisition"));

        transportManager.registerChannelListener(subscriptionManager);

        subscriptionManager.start();
    }

    @Override
    public void close()
    {
        subscriptionManager.closeAllSubscriptions();
        subscriptionManager.stop();

        transportManager.close();
    }

    @Override
    public TaskTopicClientImpl taskTopic(final String topicName, final int partitionId)
    {
        ensureNotNullOrEmpty("topic name", topicName);
        ensureGreaterThanOrEqual("partition id", partitionId, 0);
        return new TaskTopicClientImpl(this, topicName, partitionId);
    }

    @Override
    public WorkflowTopicClient workflowTopic(final String topicName, final int partitionId)
    {
        ensureNotNullOrEmpty("topic name", topicName);
        ensureGreaterThanOrEqual("partition id", partitionId, 0);
        return new WorkflowTopicClientImpl(this, topicName, partitionId);
    }

    @Override
    public TopicClientImpl topic(final String topicName, final int partitionId)
    {
        ensureNotNullOrEmpty("topic name", topicName);
        ensureGreaterThanOrEqual("partition id", partitionId, 0);
        return new TopicClientImpl(this, topicName, partitionId);
    }

    @Override
    public TransportConnection openConnection()
    {
        return transportManager.openConnection();
    }

    public ClientCmdExecutor getCmdExecutor()
    {
        return cmdExecutor;
    }

    public TransportManager getTransportManager()
    {
        return transportManager;
    }

    public SubscriptionManager getSubscriptionManager()
    {
        return subscriptionManager;
    }

    public ObjectMapper getObjectMapper()
    {
        return objectMapper;
    }

    public Properties getInitializationProperties()
    {
        return initializationProperties;
    }

}
