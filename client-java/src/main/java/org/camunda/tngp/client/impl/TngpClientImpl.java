package org.camunda.tngp.client.impl;

import static org.camunda.tngp.client.ClientProperties.*;
import static org.camunda.tngp.util.EnsureUtil.ensureGreaterThanOrEqual;
import static org.camunda.tngp.util.EnsureUtil.ensureNotNullOrEmpty;

import java.util.Properties;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.tngp.client.ClientProperties;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.WorkflowTopicClient;
import org.camunda.tngp.client.clustering.RequestTopologyCmd;
import org.camunda.tngp.client.clustering.impl.ClientTopologyManager;
import org.camunda.tngp.client.clustering.impl.RequestTopologyCmdImpl;
import org.camunda.tngp.client.event.impl.TopicClientImpl;
import org.camunda.tngp.client.task.impl.subscription.SubscriptionManager;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.transport.*;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.util.actor.ActorReference;
import org.camunda.tngp.util.actor.ActorScheduler;
import org.camunda.tngp.util.actor.ActorSchedulerBuilder;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class TngpClientImpl implements TngpClient
{
    protected final Properties initializationProperties;

    protected final Transport transport;
    protected final TransportConnectionPool connectionPool;
    protected SocketAddress contactPoint;
    protected Dispatcher dataFrameReceiveBuffer;
    protected ActorScheduler transportActorScheduler;

    protected final ObjectMapper objectMapper;

    protected SubscriptionManager subscriptionManager;

    protected ChannelManager channelManager;

    protected final ClientTopologyManager topologyManager;
    protected final ClientCommandManager commandManager;

    protected ActorReference topologyManagerActorReference;
    protected ActorReference commandManagerActorReference;

    protected boolean connected = false;


    public TngpClientImpl(final Properties properties)
    {
        ClientProperties.setDefaults(properties);
        this.initializationProperties = properties;

        contactPoint = SocketAddress.from(properties.getProperty(BROKER_CONTACTPOINT));

        final int maxConnections = Integer.parseInt(properties.getProperty(CLIENT_MAXCONNECTIONS));
        final int maxRequests = Integer.parseInt(properties.getProperty(CLIENT_MAXREQUESTS));
        final int sendBufferSize = Integer.parseInt(properties.getProperty(CLIENT_SENDBUFFER_SIZE));

        this.transportActorScheduler = ActorSchedulerBuilder.createDefaultScheduler();

        final TransportBuilder transportBuilder = Transports.createTransport("tngp.client")
            .sendBufferSize(1024 * 1024 * sendBufferSize)
            .maxMessageLength(1024 * 1024)
            .actorScheduler(transportActorScheduler);

        if (properties.containsKey(CLIENT_TCP_CHANNEL_KEEP_ALIVE_PERIOD))
        {
            transportBuilder.channelKeepAlivePeriod(Long.parseLong(properties.getProperty(CLIENT_TCP_CHANNEL_KEEP_ALIVE_PERIOD)));
        }

        transport = transportBuilder
            .build();

        dataFrameReceiveBuffer = Dispatchers.create("receive-buffer")
            .bufferSize(1024 * 1024 * sendBufferSize)
            .modePubSub()
            .frameMaxLength(1024 * 1024)
            .actorScheduler(transportActorScheduler)
            .build();

        connectionPool = TransportConnectionPool.newFixedCapacityPool(transport, maxConnections, maxRequests);

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
                dataFrameReceiveBuffer.openSubscription("task-acquisition"));
        transport.registerChannelListener(subscriptionManager);

        channelManager = transport.createClientChannelPool()
                .requestResponseProtocol(connectionPool)
                .transportChannelHandler(Protocols.FULL_DUPLEX_SINGLE_MESSAGE, new ReceiveBufferChannelHandler(dataFrameReceiveBuffer))
                .build();

        topologyManager = new ClientTopologyManager(channelManager, connectionPool, objectMapper, contactPoint);
        commandManager = new ClientCommandManager(channelManager, connectionPool, topologyManager);
    }

    @Override
    public void connect()
    {
        commandManagerActorReference = transportActorScheduler.schedule(commandManager);
        topologyManagerActorReference = transportActorScheduler.schedule(topologyManager);

        subscriptionManager.start();

        connected = true;
    }

    @Override
    public void disconnect()
    {
        if (connected)
        {
            subscriptionManager.closeAllSubscriptions();
            subscriptionManager.stop();

            topologyManagerActorReference.close();
            topologyManagerActorReference = null;

            commandManagerActorReference.close();
            commandManagerActorReference = null;

            channelManager.closeAllChannelsAsync().join();

            connected = false;
        }
    }

    @Override
    public void close()
    {
        disconnect();

        subscriptionManager.close();

        try
        {
            connectionPool.close();
        }
        catch (final Exception e)
        {
            e.printStackTrace();
        }

        try
        {
            transport.close();
        }
        catch (final Exception e)
        {
            e.printStackTrace();
        }

        try
        {
            dataFrameReceiveBuffer.close();
        }
        catch (final Exception e)
        {
            e.printStackTrace();
        }

        transportActorScheduler.close();
    }

    @Override
    public RequestTopologyCmd requestTopology()
    {
        return new RequestTopologyCmdImpl(commandManager, objectMapper);
    }

    @Override
    public TransportConnectionPool getConnectionPool()
    {
        return connectionPool;
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

    public ClientCommandManager getCommandManager()
    {
        return commandManager;
    }

    public ClientTopologyManager getTopologyManager()
    {
        return topologyManager;
    }

    public SubscriptionManager getSubscriptionManager()
    {
        return subscriptionManager;
    }

    public ChannelManager getChannelManager()
    {
        return channelManager;
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
