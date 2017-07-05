package io.zeebe.client.impl;

import static io.zeebe.client.ClientProperties.*;
import static io.zeebe.util.EnsureUtil.ensureGreaterThanOrEqual;
import static io.zeebe.util.EnsureUtil.ensureNotNullOrEmpty;

import java.util.Properties;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.client.ClientProperties;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.WorkflowTopicClient;
import io.zeebe.client.clustering.RequestTopologyCmd;
import io.zeebe.client.clustering.impl.ClientTopologyManager;
import io.zeebe.client.clustering.impl.RequestTopologyCmdImpl;
import io.zeebe.client.event.impl.TopicClientImpl;
import io.zeebe.client.task.impl.subscription.SubscriptionManager;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.transport.*;
import io.zeebe.transport.protocol.Protocols;
import io.zeebe.transport.requestresponse.client.TransportConnectionPool;
import io.zeebe.util.actor.ActorReference;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import org.msgpack.jackson.dataformat.MessagePackFactory;

public class ZeebeClientImpl implements ZeebeClient
{
    protected final Properties initializationProperties;

    protected final Transport transport;
    protected final TransportConnectionPool connectionPool;
    protected SocketAddress contactPoint;
    protected Dispatcher dataFrameReceiveBuffer;

    protected ActorScheduler actorScheduler;

    protected final ObjectMapper objectMapper;

    protected SubscriptionManager subscriptionManager;

    protected ChannelManager channelManager;

    protected final ClientTopologyManager topologyManager;
    protected final ClientCommandManager commandManager;

    protected ActorReference topologyManagerActorReference;
    protected ActorReference commandManagerActorReference;

    protected boolean connected = false;


    public ZeebeClientImpl(final Properties properties)
    {
        ClientProperties.setDefaults(properties);
        this.initializationProperties = properties;

        contactPoint = SocketAddress.from(properties.getProperty(BROKER_CONTACTPOINT));

        final int maxConnections = Integer.parseInt(properties.getProperty(CLIENT_MAXCONNECTIONS));
        final int maxRequests = Integer.parseInt(properties.getProperty(CLIENT_MAXREQUESTS));
        final int sendBufferSize = Integer.parseInt(properties.getProperty(CLIENT_SENDBUFFER_SIZE));
        final int numExecutionThreads = Integer.parseInt(properties.getProperty(CLIENT_EXECUTION_THREADS));

        this.actorScheduler = ActorSchedulerBuilder.createDefaultScheduler(numExecutionThreads);

        final TransportBuilder transportBuilder = Transports.createTransport("zeebe.client")
            .sendBufferSize(1024 * 1024 * sendBufferSize)
            .maxMessageLength(1024 * 1024)
            .actorScheduler(actorScheduler);

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
            .actorScheduler(actorScheduler)
            .build();

        connectionPool = TransportConnectionPool.newFixedCapacityPool(transport, maxConnections, maxRequests);

        objectMapper = new ObjectMapper(new MessagePackFactory());
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        final Boolean autoCompleteTasks = Boolean.parseBoolean(properties.getProperty(CLIENT_TASK_EXECUTION_AUTOCOMPLETE));

        final int prefetchCapacity = Integer.parseInt(properties.getProperty(ClientProperties.CLIENT_TOPIC_SUBSCRIPTION_PREFETCH_CAPACITY));
        subscriptionManager = new SubscriptionManager(
                this,
                actorScheduler,
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
        commandManagerActorReference = actorScheduler.schedule(commandManager);
        topologyManagerActorReference = actorScheduler.schedule(topologyManager);

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

        actorScheduler.close();
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
