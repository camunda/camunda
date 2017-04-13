package org.camunda.tngp.test.broker.protocol.brokerapi;

import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.protocol.clientapi.EventType;
import org.camunda.tngp.test.broker.protocol.MsgPackHelper;
import org.camunda.tngp.test.util.collection.MapFactoryBuilder;
import org.camunda.tngp.transport.ServerSocketBinding;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.camunda.tngp.transport.Transports;
import org.junit.rules.ExternalResource;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

public class StubBrokerRule extends ExternalResource
{
    protected final String host;
    protected final int port;

    protected Transport transport;
    protected ServerSocketBinding serverSocketBinding;

    protected StubResponseChannelHandler channelHandler;
    protected MsgPackHelper msgPackHelper;
    private InetSocketAddress bindAddr;

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
                .threadingMode(ThreadingMode.SHARED)
                .build();

        bindAddr = new InetSocketAddress(host, port);

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

    public MapFactoryBuilder<ExecuteCommandRequest, ExecuteCommandResponseBuilder> onWorkflowRequestRespondWith()
    {
        return onWorkflowRequestRespondWith(0, 123);
    }

    public MapFactoryBuilder<ExecuteCommandRequest, ExecuteCommandResponseBuilder> onWorkflowRequestRespondWith(int topicId, long longkey)
    {
        final MapFactoryBuilder<ExecuteCommandRequest, ExecuteCommandResponseBuilder> eventType = onExecuteCommandRequest(ecr -> ecr.eventType() == EventType.WORKFLOW_EVENT &&
            "CREATE_WORKFLOW_INSTANCE".equals(ecr.getCommand().get("eventType")))
            .respondWith()
            .topicId(topicId)
            .longKey(longkey).event().allOf((r) -> r.getCommand());
        return eventType;
//            .event(workflowInstanceEvent)
//            .register();
    }

    public ExecuteCommandResponseBuilder onExecuteCommandRequest()
    {
        return onExecuteCommandRequest((r) -> true);
    }

    public ExecuteCommandResponseBuilder onExecuteCommandRequest(Predicate<ExecuteCommandRequest> activationFunction)
    {
        return new ExecuteCommandResponseBuilder(channelHandler::addExecuteCommandRequestStub, msgPackHelper, activationFunction);
    }




    public ControlMessageResponseBuilder onControlMessageRequest()
    {
        return onControlMessageRequest((r) -> true);
    }

    public ControlMessageResponseBuilder onControlMessageRequest(Predicate<ControlMessageRequest> activationFunction)
    {
        return new ControlMessageResponseBuilder(channelHandler::addControlMessageRequestStub, msgPackHelper, activationFunction);
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

    public void stubTopicSubscriptionApi(long initialId)
    {
        final AtomicLong subscriberKeyProvider = new AtomicLong(initialId);
        final AtomicLong subscriptionKeyProvider = new AtomicLong(0);

        onExecuteCommandRequest((r) -> r.eventType() == EventType.SUBSCRIBER_EVENT
                && "SUBSCRIBE".equals(r.getCommand().get("eventType")))
            .respondWith()
            .longKey((r) -> subscriberKeyProvider.getAndIncrement())
            .topicId((r) -> r.topicId())
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
            .longKey((r) -> subscriptionKeyProvider.getAndIncrement())
            .topicId((r) -> r.topicId())
            .event()
                .allOf((r) -> r.getCommand())
                .put("eventType", "ACKNOWLEDGED")
                .done()
            .register();
    }
}
