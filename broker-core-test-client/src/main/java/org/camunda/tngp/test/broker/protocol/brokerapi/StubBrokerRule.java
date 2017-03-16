package org.camunda.tngp.test.broker.protocol.brokerapi;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.transport.ServerSocketBinding;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.camunda.tngp.transport.Transports;
import org.junit.rules.ExternalResource;

public class StubBrokerRule extends ExternalResource
{
    protected final String host;
    protected final int port;

    protected Transport transport;
    protected ServerSocketBinding serverSocketBinding;

    protected StubResponseChannelHandler channelHandler;
    protected MsgPackHelper msgPackHelper;

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

        final InetSocketAddress bindAddr = new InetSocketAddress(host, port);

        channelHandler = new StubResponseChannelHandler(transport.getSendBuffer(), msgPackHelper);

        serverSocketBinding = transport.createServerSocketBinding(bindAddr)
            .transportChannelHandler(channelHandler)
            .bind();
    }

    @Override
    protected void after()
    {
        if (serverSocketBinding != null)
        {
            serverSocketBinding.close();
        }
        if (transport != null)
        {
            transport.close();
        }

    }

    public ExecuteCommandResponseBuilder onExecuteCommandRequest()
    {
        return onExecuteCommandRequest((r) -> true);
    }

    public ExecuteCommandResponseBuilder onExecuteCommandRequest(Function<ExecuteCommandRequest, Boolean> activationFunction)
    {
        return new ExecuteCommandResponseBuilder(channelHandler::addExecuteCommandRequestStub, msgPackHelper, activationFunction);
    }

    public ControlMessageResponseBuilder onControlMessageRequest()
    {
        return onControlMessageRequest((r) -> true);
    }

    public ControlMessageResponseBuilder onControlMessageRequest(Function<ControlMessageRequest, Boolean> activationFunction)
    {
        return new ControlMessageResponseBuilder(channelHandler::addControlMessageRequestStub, msgPackHelper, activationFunction);
    }

    public List<ControlMessageRequest> getReceivedControlMessageRequests()
    {
        return channelHandler.getReceivedControlMessageRequests();
    }

    public SubscribedEventBuilder newSubscribedEvent()
    {
        return new SubscribedEventBuilder(msgPackHelper, transport.getSendBuffer());
    }

    public void stubTopicSubscriptionApi(long initialId)
    {
        final AtomicLong idGenerator = new AtomicLong(initialId);

        onControlMessageRequest((r) -> r.messageType() == ControlMessageType.ADD_TOPIC_SUBSCRIPTION)
            .respondWith()
            .data()
                .allOf((r) -> r.getData())
                .put("id", idGenerator.getAndIncrement())
                .done()
            .register();

        onControlMessageRequest((r) -> r.messageType() == ControlMessageType.REMOVE_TOPIC_SUBSCRIPTION)
            .respondWith()
            .data()
                .allOf((r) -> r.getData())
                .done()
            .register();

        onControlMessageRequest((r) -> r.messageType() == ControlMessageType.ACKNOWLEDGE_TOPIC_EVENT)
            .respondWith()
            .data()
                .allOf((r) -> r.getData())
                .done()
            .register();
    }
}
