package org.camunda.tngp.test.broker.protocol.clientapi;

import java.util.Arrays;
import java.util.stream.Stream;

import org.agrona.DirectBuffer;
import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.ExecuteCommandResponseDecoder;
import org.camunda.tngp.protocol.clientapi.MessageHeaderDecoder;
import org.camunda.tngp.protocol.clientapi.SubscribedEventDecoder;
import org.camunda.tngp.test.broker.protocol.MsgPackHelper;
import org.camunda.tngp.transport.Channel;
import org.camunda.tngp.transport.ChannelManager;
import org.camunda.tngp.transport.SocketAddress;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.camunda.tngp.transport.Transports;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.requestresponse.client.RequestResponseChannelHandler;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPoolImpl;
import org.camunda.tngp.transport.spi.TransportChannelHandler;
import org.junit.rules.ExternalResource;

public class ClientApiRule extends ExternalResource
{
    public static final String DEFAULT_TOPIC_NAME = "default-topic";
    public static final int DEFAULT_PARTITION_ID = 0;

    protected Transport transport;

    protected final int port = 51015;
    protected final String host = "localhost";

    protected ChannelManager clientChannelPool;
    protected Channel clientChannel;

    protected TransportConnectionPool connectionPool;

    protected MsgPackHelper msgPackHelper;
    protected RawMessageCollector incomingMessageCollector;

    @Override
    protected void before() throws Throwable
    {
        incomingMessageCollector = new RawMessageCollector();
        transport = Transports.createTransport("testTransport")
                .threadingMode(ThreadingMode.SHARED)
                .build();

        connectionPool = TransportConnectionPool.newFixedCapacityPool(transport, 2, 64);

        final TransportChannelHandler requestResponseHandler = broadcastingHandler(
                incomingMessageCollector,
                new RequestResponseChannelHandler((TransportConnectionPoolImpl) connectionPool));

        clientChannelPool = transport.createClientChannelPool()
                .transportChannelHandler(Protocols.REQUEST_RESPONSE, requestResponseHandler)
                .transportChannelHandler(Protocols.FULL_DUPLEX_SINGLE_MESSAGE, incomingMessageCollector)
                .build();

        openChannel();

        msgPackHelper = new MsgPackHelper();
    }

    @Override
    protected void after()
    {

        if (connectionPool != null)
        {
            connectionPool.close();
        }

        if (clientChannel != null)
        {
            closeChannel();
        }

        if (transport != null)
        {
            transport.close();
        }
    }

    public ExecuteCommandRequestBuilder createCmdRequest()
    {
        return new ExecuteCommandRequestBuilder(connectionPool, clientChannel.getStreamId(), msgPackHelper);
    }

    public ControlMessageRequestBuilder createControlMessageRequest()
    {
        return new ControlMessageRequestBuilder(connectionPool, clientChannel.getStreamId(), msgPackHelper);
    }


    public ClientApiRule moveMessageStreamToTail()
    {
        incomingMessageCollector.moveToTail();
        return this;
    }

    public ClientApiRule moveMessageStreamToHead()
    {
        incomingMessageCollector.moveToHead();
        return this;
    }

    public int numSubscribedEventsAvailable()
    {
        return (int) incomingMessageCollector.getNumMessagesFulfilling(this::isSubscribedEvent);
    }

    public TestTopicClient topic()
    {
        return topic(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);
    }

    public TestTopicClient topic(final String topicName, final int partitionId)
    {
        return new TestTopicClient(this, topicName, partitionId);
    }

    public ExecuteCommandRequest openTopicSubscription(final String name, final long startPosition)
    {
        return openTopicSubscription(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID, name, startPosition);
    }

    public ExecuteCommandRequest openTopicSubscription(final String topicName, final int partitionId, final String name, final long startPosition)
    {
        return createCmdRequest()
            .topicName(topicName)
            .partitionId(partitionId)
            .eventTypeSubscriber()
            .command()
                .put("startPosition", startPosition)
                .put("name", name)
                .put("eventType", "SUBSCRIBE")
                .done()
            .send();
    }

    public ControlMessageRequest openTaskSubscription(final String type)
    {
        return openTaskSubscription(DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID, type);
    }

    public ControlMessageRequest openTaskSubscription(final String topicName, final int partitionId, final String type)
    {
        return createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .data()
                .put("topicName", topicName)
                .put("partitionId", partitionId)
                .put("taskType", type)
                .put("lockDuration", 1000L)
                .put("lockOwner", 0)
                .put("credits", 10)
                .done()
            .send();
    }

    public Stream<RawMessage> incomingMessages()
    {
        return Stream.generate(incomingMessageCollector);
    }

    /**
     * @return an infinite stream of received subscribed events; make sure to use short-circuiting operations
     *   to reduce it to a finite stream
     */
    public Stream<SubscribedEvent> subscribedEvents()
    {
        return incomingMessages().filter(this::isSubscribedEvent)
                .map(this::asSubscribedEvent);
    }

    public Stream<RawMessage> commandResponses()
    {
        return incomingMessages().filter(this::isCommandResponse);
    }

    protected SubscribedEvent asSubscribedEvent(RawMessage message)
    {
        final SubscribedEvent event = new SubscribedEvent(message);
        event.wrap(message.getMessage(), 0, message.getMessage().capacity());
        return event;
    }

    protected boolean isCommandResponse(RawMessage message)
    {
        return message.getProtocolId() == Protocols.REQUEST_RESPONSE &&
                isMessageOfType(message.getMessage(), ExecuteCommandResponseDecoder.TEMPLATE_ID);
    }

    protected boolean isSubscribedEvent(RawMessage message)
    {
        return message.getProtocolId() == Protocols.FULL_DUPLEX_SINGLE_MESSAGE &&
                isMessageOfType(message.getMessage(), SubscribedEventDecoder.TEMPLATE_ID);

    }

    protected boolean isMessageOfType(DirectBuffer message, int type)
    {
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        headerDecoder.wrap(message, 0);

        return headerDecoder.templateId() == type;
    }

    public void closeChannel()
    {
        if (clientChannel == null)
        {
            throw new RuntimeException("No channel open");
        }

        clientChannelPool.closeAllChannelsAsync().join();
        clientChannel = null;
    }

    public void openChannel()
    {
        if (clientChannel != null)
        {
            throw new RuntimeException("Cannot open more than one channel at once");
        }

        clientChannel = clientChannelPool.requestChannel(new SocketAddress(host, port));
    }

    protected TransportChannelHandler broadcastingHandler(TransportChannelHandler... handlers)
    {
        return new TransportChannelHandler()
        {

            @Override
            public boolean onControlFrame(Channel transportChannel, DirectBuffer buffer, int offset, int length)
            {
                Arrays.stream(handlers).forEach((h) -> h.onControlFrame(transportChannel, buffer, offset, length));
                return true;
            }

            @Override
            public void onChannelSendError(Channel transportChannel, DirectBuffer buffer, int offset, int length)
            {
                Arrays.stream(handlers).forEach((h) -> h.onChannelSendError(transportChannel, buffer, offset, length));
            }

            @Override
            public boolean onChannelReceive(Channel transportChannel, DirectBuffer buffer, int offset, int length)
            {
                return Arrays.stream(handlers)
                        .map((h) -> h.onChannelReceive(transportChannel, buffer, offset, length))
                        .allMatch((b) -> true);
            }

            @Override
            public void onChannelOpened(Channel transportChannel)
            {
                Arrays.stream(handlers).forEach((h) -> h.onChannelOpened(transportChannel));
            }

            @Override
            public void onChannelClosed(Channel transportChannel)
            {
                Arrays.stream(handlers).forEach((h) -> h.onChannelClosed(transportChannel));
            }
        };
    }

}
