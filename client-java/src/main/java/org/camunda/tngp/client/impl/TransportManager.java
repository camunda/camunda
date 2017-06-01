package org.camunda.tngp.client.impl;

import static org.camunda.tngp.util.EnsureUtil.*;

import java.util.HashMap;
import java.util.Map;

import org.camunda.tngp.client.cmd.ClientCommand;
import org.camunda.tngp.client.impl.cmd.TopicCommand;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.Channel;
import org.camunda.tngp.transport.ChannelManager;
import org.camunda.tngp.transport.ReceiveBufferChannelHandler;
import org.camunda.tngp.transport.SocketAddress;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.TransportBuilder;
import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.camunda.tngp.transport.TransportChannelListener;
import org.camunda.tngp.transport.Transports;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;


public class TransportManager implements AutoCloseable
{

    private final Transport transport;
    private final Dispatcher dataFrameReceiveBuffer;
    private final TransportConnectionPool connectionPool;
    private final ChannelManager channelManager;

    private final Map<SocketAddress, Channel> channelForBroker = new HashMap<>();
    private final Map<Topic, SocketAddress> brokerForTopic = new HashMap<>();

    public TransportManager(final BrokerManagerBuilder builder)
    {
        final TransportBuilder transportBuilder = Transports.createTransport("tngp.client")
            .sendBufferSize(builder.sendBufferSize)
            .maxMessageLength(builder.maxMessageSize)
            .threadingMode(builder.threadingMode);

        if (builder.keepAlivePeriod != null)
        {
            transportBuilder.channelKeepAlivePeriod(builder.keepAlivePeriod);
        }

        transport = transportBuilder
            .build();

        dataFrameReceiveBuffer = Dispatchers.create("receive-buffer")
            .bufferSize(builder.sendBufferSize)
            .modePubSub()
            .frameMaxLength(builder.maxMessageSize)
            .build();

        connectionPool = TransportConnectionPool.newFixedCapacityPool(transport, builder.maxConnections, builder.maxRequests);

        channelManager = transport.createClientChannelPool()
            .requestResponseProtocol(connectionPool)
            .transportChannelHandler(Protocols.FULL_DUPLEX_SINGLE_MESSAGE, new ReceiveBufferChannelHandler(dataFrameReceiveBuffer))
            .build();
    }

    public static BrokerManagerBuilder create()
    {
        return new BrokerManagerBuilder();
    }

    public Channel registerBroker(final SocketAddress socketAddress)
    {
        final Channel channel = channelManager.requestChannel(socketAddress);
        channelForBroker.put(socketAddress, channel);
        return channel;
    }

    public void registerBrokerForTopic(final Topic topic, final SocketAddress socketAddress)
    {
        brokerForTopic.put(topic, socketAddress);
    }

    public void registerChannelListener(final TransportChannelListener channelListener)
    {
        transport.registerChannelListener(channelListener);
    }

    public Subscription openSubscription(final String subscriptionName)
    {
        return dataFrameReceiveBuffer.openSubscription(subscriptionName);
    }

    public TransportConnection openConnection()
    {
        return connectionPool.openConnection();
    }

    public <R> Channel getChannelForCommand(final ClientCommand<R> cmd)
    {
        if (cmd instanceof TopicCommand)
        {
            final Topic topic = ((TopicCommand) cmd).getTopic();
            return getChannelForTopic(topic);
        }
        else
        {
            return getRandomChannel();
        }
    }

    public Channel getChannelForTopic(final Topic topic)
    {
        final SocketAddress broker = brokerForTopic.get(topic);

        final Channel channel;
        if (broker != null)
        {
            channel = getChannelForBroker(broker);
        }
        else
        {
            // TODO(menski): either search for broker by fetching topology (#228) or
            //                 randomly select one and expect error with broker hint (#287)
            channel = getRandomChannel();
        }

        if (channel == null)
        {
            throw new RuntimeException("Unable to find broker for topic: " + topic);
        }

        return channel;
    }

    private Channel getChannelForBroker(final SocketAddress broker)
    {
        return channelForBroker.computeIfAbsent(broker, this::registerBroker);
    }

    private Channel getRandomChannel()
    {
        // "randomly" chose the first channel
        final Channel channel = channelForBroker.values().iterator().next();

        if (channel == null)
        {
            throw new RuntimeException("Unable to find any broker to connect to");
        }

        return channel;
    }


    @Override
    public void close()
    {
        channelManager.closeAllChannelsAsync().join();

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

    static class BrokerManagerBuilder
    {
        private int sendBufferSize;
        private ThreadingMode threadingMode;
        private Long keepAlivePeriod;
        private int maxMessageSize;
        private int maxConnections;
        private int maxRequests;

        public BrokerManagerBuilder sendBufferSize(final int sendBufferSize)
        {
            this.sendBufferSize = sendBufferSize * 1024 * 1024;
            return this;
        }

        public BrokerManagerBuilder sendBufferSize(final String sendBufferSize)
        {
            return sendBufferSize(Integer.valueOf(sendBufferSize));
        }

        public BrokerManagerBuilder threadingMode(final ThreadingMode threadingMode)
        {
            this.threadingMode = threadingMode;
            return this;
        }

        public BrokerManagerBuilder threadingMode(final String threadingMode)
        {
            return threadingMode(ThreadingMode.valueOf(threadingMode));
        }

        public BrokerManagerBuilder keepAlivePeriod(final long keepAlivePeriod)
        {
            this.keepAlivePeriod = keepAlivePeriod;
            return this;
        }

        public BrokerManagerBuilder keepAlivePeriod(final String keepAlivePeriod)
        {
            if (keepAlivePeriod != null)
            {
                return keepAlivePeriod(Long.valueOf(keepAlivePeriod));
            }

            return this;
        }

        public BrokerManagerBuilder maxMessageSize(final int maxMessageSize)
        {
            this.maxMessageSize = maxMessageSize;
            return this;
        }

        public BrokerManagerBuilder maxMessageSize(final String maxMessageSize)
        {
            return maxConnections(Integer.valueOf(maxMessageSize));
        }

        public BrokerManagerBuilder maxConnections(final int maxConnections)
        {
            this.maxConnections = maxConnections;
            return this;
        }

        public BrokerManagerBuilder maxConnections(final String maxConnections)
        {
            return maxConnections(Integer.valueOf(maxConnections));
        }

        public BrokerManagerBuilder maxRequests(final int maxRequests)
        {
            this.maxRequests = maxRequests;
            return this;
        }

        public BrokerManagerBuilder maxRequests(final String maxRequests)
        {
            return maxRequests(Integer.valueOf(maxRequests));
        }

        public void validate()
        {
            ensureGreaterThan("sendBufferSize", sendBufferSize, 0);
            ensureNotNull("threadingMode", threadingMode);
            if (keepAlivePeriod != null)
            {
                ensureGreaterThan("keepAlivePeriod", keepAlivePeriod, 0);
            }
            ensureGreaterThan("maxMessageSize", maxMessageSize, 0);
            ensureGreaterThan("maxConnections", maxConnections, 0);
            ensureGreaterThan("maxRequests", maxRequests, 0);
        }

        public TransportManager build()
        {
            validate();
            return new TransportManager(this);
        }

    }

}
