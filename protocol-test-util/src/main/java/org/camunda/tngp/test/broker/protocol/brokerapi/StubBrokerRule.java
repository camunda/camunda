package org.camunda.tngp.test.broker.protocol.brokerapi;


import static org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_PARTITION_ID;
import static org.camunda.tngp.test.broker.protocol.clientapi.ClientApiRule.DEFAULT_TOPIC_NAME;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.protocol.clientapi.SubscriptionType;
import org.camunda.tngp.test.broker.protocol.MsgPackHelper;
import org.camunda.tngp.test.util.collection.MapFactoryBuilder;
import org.camunda.tngp.transport.ServerSocketBinding;
import org.camunda.tngp.transport.SocketAddress;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.Transports;
import org.camunda.tngp.transport.impl.ServerSocketBindingImpl;
import org.camunda.tngp.util.actor.ActorSchedulerImpl;
import org.junit.rules.ExternalResource;

public class StubBrokerRule extends ExternalResource
{

    public static final String TEST_TOPIC_NAME = "test-topic";
    public static final int TEST_PARTITION_ID = 0;

    protected final String host;
    protected final int port;

    protected Transport transport;
    protected ServerSocketBinding serverSocketBinding;

    protected StubResponseChannelHandler channelHandler;
    protected MsgPackHelper msgPackHelper;
    private SocketAddress bindAddr;

    public StubBrokerRule()
    {
        this("localhost", 51015);
    }

    public StubBrokerRule(String host, int port)
    {
        this.host = host;
        this.port = port;
    }

    @Override
    protected void before() throws Throwable
    {
        msgPackHelper = new MsgPackHelper();

        transport = Transports.createTransport("testTransport")
                .actorScheduler(ActorSchedulerImpl.createDefaultScheduler())
                .build();

        bindAddr = new SocketAddress(host, port);

        channelHandler = new StubResponseChannelHandler(transport.getSendBuffer(), msgPackHelper);

        openServerSocketBinding();
    }

    @Override
    protected void after()
    {
        if (serverSocketBinding != null)
        {
            closeServerSocketBinding();
        }
        if (transport != null)
        {
            transport.close();
        }
    }

    public void closeServerSocketBinding()
    {
        if (serverSocketBinding == null)
        {
            throw new RuntimeException("No open server socket binding");
        }

        serverSocketBinding.close();
        serverSocketBinding = null;
    }

    public void interruptAllServerChannels()
    {
        ((ServerSocketBindingImpl) serverSocketBinding).interruptAllChannels().join();
    }

    public void openServerSocketBinding()
    {
        if (serverSocketBinding != null)
        {
            throw new RuntimeException("Server socket binding already open");
        }

        serverSocketBinding = transport.createServerSocketBinding(bindAddr)
            .transportChannelHandler(channelHandler)
            .bind();
    }

    public MapFactoryBuilder<ExecuteCommandRequest, ExecuteCommandResponseBuilder> onWorkflowRequestRespondWith(long key)
    {
        return onWorkflowRequestRespondWith(TEST_TOPIC_NAME, TEST_PARTITION_ID, key);
    }

    public MapFactoryBuilder<ExecuteCommandRequest, ExecuteCommandResponseBuilder> onWorkflowRequestRespondWith(final String topicName, final int partitionId, final long key)
    {
        final MapFactoryBuilder<ExecuteCommandRequest, ExecuteCommandResponseBuilder> eventType = onExecuteCommandRequest(ecr -> ecr.eventType() == EventType.WORKFLOW_EVENT)
            .respondWith()
            .topicName(topicName)
            .partitionId(partitionId)
            .key(key)
            .event()
            .allOf((r) -> r.getCommand());

        return eventType;
    }

    public ResponseBuilder<ExecuteCommandResponseBuilder, ErrorResponseBuilder<ExecuteCommandRequest>> onExecuteCommandRequest()
    {
        return onExecuteCommandRequest((r) -> true);
    }

    public ResponseBuilder<ExecuteCommandResponseBuilder, ErrorResponseBuilder<ExecuteCommandRequest>> onExecuteCommandRequest(Predicate<ExecuteCommandRequest> activationFunction)
    {
        return new ResponseBuilder<>(
                new ExecuteCommandResponseBuilder(channelHandler::addExecuteCommandRequestStub, msgPackHelper, activationFunction),
                new ErrorResponseBuilder<>(channelHandler::addExecuteCommandRequestStub, msgPackHelper, activationFunction));
    }

    public ResponseBuilder<ControlMessageResponseBuilder, ErrorResponseBuilder<ControlMessageRequest>> onControlMessageRequest()
    {
        return onControlMessageRequest((r) -> true);
    }

    public ResponseBuilder<ControlMessageResponseBuilder, ErrorResponseBuilder<ControlMessageRequest>> onControlMessageRequest(Predicate<ControlMessageRequest> activationFunction)
    {
        return new ResponseBuilder<>(
                new ControlMessageResponseBuilder(channelHandler::addControlMessageRequestStub, msgPackHelper, activationFunction),
                new ErrorResponseBuilder<>(channelHandler::addControlMessageRequestStub, msgPackHelper, activationFunction));
    }

    public List<ControlMessageRequest> getReceivedControlMessageRequests()
    {
        return channelHandler.getReceivedControlMessageRequests();
    }

    public List<ExecuteCommandRequest> getReceivedCommandRequests()
    {
        return channelHandler.getReceivedCommandRequests();
    }

    public List<Object> getAllReceivedRequests()
    {
        return channelHandler.getAllReceivedRequests();
    }

    public SubscribedEventBuilder newSubscribedEvent()
    {
        return new SubscribedEventBuilder(msgPackHelper, transport.getSendBuffer());
    }

    public void stubTopicSubscriptionApi(long initialSubscriberKey)
    {
        final AtomicLong subscriberKeyProvider = new AtomicLong(initialSubscriberKey);
        final AtomicLong subscriptionKeyProvider = new AtomicLong(0);

        onExecuteCommandRequest((r) -> r.eventType() == EventType.SUBSCRIBER_EVENT
                && "SUBSCRIBE".equals(r.getCommand().get("eventType")))
            .respondWith()
            .key((r) -> subscriberKeyProvider.getAndIncrement())
            .topicName((r) -> r.topicName())
            .partitionId((r) -> r.partitionId())
            .event()
                .allOf((r) -> r.getCommand())
                .put("eventType", "SUBSCRIBED")
                .done()
            .register();

        onControlMessageRequest((r) -> r.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .respondWith()
            .data()
                .allOf((r) -> r.getData())
                .done()
            .register();

        onExecuteCommandRequest((r) -> r.eventType() == EventType.SUBSCRIPTION_EVENT
                && "ACKNOWLEDGE".equals(r.getCommand().get("eventType")))
            .respondWith()
            .key((r) -> subscriptionKeyProvider.getAndIncrement())
            .topicName((r) -> r.topicName())
            .partitionId((r) -> r.partitionId())
            .event()
                .allOf((r) -> r.getCommand())
                .put("eventType", "ACKNOWLEDGED")
                .done()
            .register();
    }

    public void stubTaskSubscriptionApi(long initialSubscriberKey)
    {
        final AtomicLong subscriberKeyProvider = new AtomicLong(initialSubscriberKey);

        onControlMessageRequest((r) -> r.messageType() == ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .respondWith()
            .data()
                .allOf((r) -> r.getData())
                .put("subscriberKey", (r) -> subscriberKeyProvider.getAndIncrement())
                .done()
            .register();

        onControlMessageRequest((r) -> r.messageType() == ControlMessageType.REMOVE_TASK_SUBSCRIPTION)
            .respondWith()
            .data()
                .allOf((r) -> r.getData())
                .done()
            .register();

        onControlMessageRequest((r) -> r.messageType() == ControlMessageType.INCREASE_TASK_SUBSCRIPTION_CREDITS)
            .respondWith()
            .data()
                .allOf((r) -> r.getData())
                .done()
            .register();
    }

    public void pushTopicEvent(int channelId, long subscriberKey, long key, long position)
    {
        pushTopicEvent(channelId, subscriberKey, key, position, EventType.RAFT_EVENT);
    }

    public void pushTopicEvent(int channelId, long subscriberKey, long key, long position, EventType eventType)
    {
        newSubscribedEvent()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .key(key)
            .position(position)
            .eventType(eventType)
            .subscriberKey(subscriberKey)
            .subscriptionType(SubscriptionType.TOPIC_SUBSCRIPTION)
            .event()
                .done()
            .push(channelId);
    }

    public void pushLockedTask(int channelId, long subscriberKey, long key, long position, String taskType)
    {
        newSubscribedEvent()
            .topicName(DEFAULT_TOPIC_NAME)
            .partitionId(DEFAULT_PARTITION_ID)
            .key(key)
            .position(position)
            .eventType(EventType.TASK_EVENT)
            .subscriberKey(subscriberKey)
            .subscriptionType(SubscriptionType.TASK_SUBSCRIPTION)
            .event()
                .put("type", taskType)
                .put("lockTime", 1000L)
                .put("retries", 3)
                .put("payload", msgPackHelper.encodeAsMsgPack(new HashMap<>()))
                .done()
            .push(channelId);
    }
}
