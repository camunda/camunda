package org.camunda.tngp.client.impl;

import static org.camunda.tngp.client.ClientProperties.BROKER_CONTACTPOINT;
import static org.camunda.tngp.client.ClientProperties.CLIENT_MAXCONNECTIONS;
import static org.camunda.tngp.client.ClientProperties.CLIENT_MAXREQUESTS;
import static org.camunda.tngp.client.ClientProperties.CLIENT_SENDBUFFER_SIZE;
import static org.camunda.tngp.client.ClientProperties.CLIENT_TASK_EXECUTION_AUTOCOMPLETE;
import static org.camunda.tngp.client.ClientProperties.CLIENT_TASK_EXECUTION_THREADS;
import static org.camunda.tngp.client.ClientProperties.CLIENT_THREADINGMODE;

import java.net.InetSocketAddress;
import java.util.Properties;

import org.camunda.tngp.client.ClientProperties;
import org.camunda.tngp.client.TngpClient;
import org.camunda.tngp.client.WorkflowTopicClient;
import org.camunda.tngp.client.event.impl.TopicClientImpl;
import org.camunda.tngp.client.impl.cmd.DummyChannelResolver;
import org.camunda.tngp.client.task.impl.SubscriptionManager;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.transport.ClientChannel;
import org.camunda.tngp.transport.ReceiveBufferChannelHandler;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.camunda.tngp.transport.Transports;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.singlemessage.DataFramePool;
import org.camunda.tngp.util.EnsureUtil;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TngpClientImpl implements TngpClient
{
    public static final int DEFAULT_RESOURCE_ID = 0;
    public static final int DEFAULT_SHARD_ID = 0;

    protected final Transport transport;
    protected final TransportConnectionPool connectionPool;
    protected final DataFramePool dataFramePool;
    protected ClientChannel channel;
    protected InetSocketAddress contactPoint;
    protected Dispatcher dataFrameReceiveBuffer;

    protected DummyChannelResolver channelResolver;
    protected ClientCmdExecutor cmdExecutor;

    protected final ObjectMapper objectMapper;

    protected SubscriptionManager subscriptionManager;

    public TngpClientImpl(final Properties properties)
    {
        ClientProperties.setDefaults(properties);

        final String brokerContactPoint = properties.getProperty(BROKER_CONTACTPOINT);

        String hostName = brokerContactPoint;
        int port = -1;

        final int portDelimiter = hostName.indexOf(":");
        if (portDelimiter != -1)
        {
            hostName = hostName.substring(0, portDelimiter);
            port = Integer.parseInt(brokerContactPoint.substring(portDelimiter + 1, brokerContactPoint.length()));
        }
        else
        {
            final String errorMessage = String.format("Tngp Client config properts %s has wrong value: '%s' Needs to have format [hostname]:[port]", BROKER_CONTACTPOINT, brokerContactPoint);
            throw new RuntimeException(errorMessage);
        }

        contactPoint = new InetSocketAddress(hostName, port);
        final int maxConnections = Integer.parseInt(properties.getProperty(CLIENT_MAXCONNECTIONS));
        final int maxRequests = Integer.parseInt(properties.getProperty(CLIENT_MAXREQUESTS));
        final int sendBufferSize = Integer.parseInt(properties.getProperty(CLIENT_SENDBUFFER_SIZE));
        final ThreadingMode threadingMode = ThreadingMode.valueOf(properties.getProperty(CLIENT_THREADINGMODE));

        transport = Transports.createTransport("tngp.client")
            .sendBufferSize(1024 * 1024 * sendBufferSize)
            .maxMessageLength(1024 * 1024)
            .threadingMode(threadingMode)
            .build();

        dataFrameReceiveBuffer = Dispatchers.create("receive-buffer")
            .bufferSize(1024 * 1024 * sendBufferSize)
            .modePubSub()
            .frameMaxLength(1024 * 1024)
            .build();

        connectionPool = TransportConnectionPool.newFixedCapacityPool(transport, maxConnections, maxRequests);
        dataFramePool = DataFramePool.newBoundedPool(maxRequests, transport.getSendBuffer());

        channelResolver = new DummyChannelResolver();

        cmdExecutor = new ClientCmdExecutor(connectionPool, dataFramePool, channelResolver);

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
    }

    @Override
    public void connect()
    {
        channel = transport.createClientChannel(contactPoint)
                .requestResponseProtocol(connectionPool)
                .transportChannelHandler(Protocols.FULL_DUPLEX_SINGLE_MESSAGE, new ReceiveBufferChannelHandler(dataFrameReceiveBuffer))
                .connect();

        channelResolver.setChannelId(channel.getId());

        subscriptionManager.start();
    }

    @Override
    public void disconnect()
    {
        subscriptionManager.closeAllSubscriptions();

        subscriptionManager.stop();

        channel.close();
        channel = null;

        channelResolver.resetChannelId();
    }

    @Override
    public void close()
    {
        if (isConnected())
        {
            disconnect();
        }

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
    }

    protected boolean isConnected()
    {
        return channel != null;
    }


    @Override
    public TransportConnectionPool getConnectionPool()
    {
        return connectionPool;
    }

    public DataFramePool getDataFramePool()
    {
        return dataFramePool;
    }

    @Override
    public TaskTopicClientImpl taskTopic(int id)
    {
        EnsureUtil.ensureGreaterThanOrEqual("id", id, 0);
        return new TaskTopicClientImpl(this, id);
    }

    @Override
    public WorkflowTopicClient workflowTopic(int id)
    {
        EnsureUtil.ensureGreaterThanOrEqual("id", id, 0);
        return new WorkflowTopicClientImpl(this, id);
    }

    @Override
    public TopicClientImpl topic(int id)
    {
        EnsureUtil.ensureGreaterThanOrEqual("id", id, 0);
        return new TopicClientImpl(this, id);
    }

    public ClientCmdExecutor getCmdExecutor()
    {
        return cmdExecutor;
    }

    public SubscriptionManager getSubscriptionManager()
    {
        return subscriptionManager;
    }

    public ObjectMapper getObjectMapper()
    {
        return objectMapper;
    }

}
