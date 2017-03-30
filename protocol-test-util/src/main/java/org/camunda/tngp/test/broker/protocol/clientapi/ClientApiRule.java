package org.camunda.tngp.test.broker.protocol.clientapi;

import java.net.InetSocketAddress;
import java.util.stream.Stream;

import org.camunda.tngp.protocol.clientapi.ControlMessageType;
import org.camunda.tngp.test.broker.protocol.MsgPackHelper;
import org.camunda.tngp.transport.ClientChannel;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.camunda.tngp.transport.Transports;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.junit.rules.ExternalResource;

public class ClientApiRule extends ExternalResource
{
    protected Transport transport;

    protected final int port = 51015;
    protected final String host = "localhost";

    protected ClientChannel clientChannel;

    protected TransportConnectionPool connectionPool;

    protected MsgPackHelper msgPackHelper;
    protected SubscribedEventCollector subscribedEventCollector;

    @Override
    protected void before() throws Throwable
    {
        subscribedEventCollector = new SubscribedEventCollector();
        transport = Transports.createTransport("testTransport")
                .threadingMode(ThreadingMode.SHARED)
                .build();

        connectionPool = TransportConnectionPool.newFixedCapacityPool(transport, 2, 64);
        openChannel();

        msgPackHelper = new MsgPackHelper();
    }

    @Override
    protected void after()
    {
        if (clientChannel != null)
        {
            closeChannel();
        }

        if (connectionPool != null)
        {
            connectionPool.close();
        }

        if (transport != null)
        {
            transport.close();
        }
    }

    public ExecuteCommandRequestBuilder createCmdRequest()
    {
        return new ExecuteCommandRequestBuilder(connectionPool, clientChannel.getId(), msgPackHelper);
    }

    public ControlMessageRequestBuilder createControlMessageRequest()
    {
        return new ControlMessageRequestBuilder(connectionPool, clientChannel.getId(), msgPackHelper);
    }

    /**
     * @return an infinite stream of received subscribed events; make sure to use short-circuiting operations
     *   to reduce it to a finite stream
     */
    public Stream<SubscribedEvent> subscribedEvents()
    {
        return Stream.generate(subscribedEventCollector);
    }

    public void moveSubscribedEventsStreamToTail()
    {
        subscribedEventCollector.moveToTail();
    }

    public int numSubscribedEventsAvailable()
    {
        return subscribedEventCollector.getPendingEvents();
    }

    public TestTopicClient topic(int topicId)
    {
        return new TestTopicClient(this, topicId);
    }

    public ExecuteCommandRequest openTopicSubscription(int topicId, String name, long startPosition)
    {
        return createCmdRequest()
            .topicId(topicId)
            .eventTypeSubscriber()
            .command()
                .put("startPosition", startPosition)
                .put("name", name)
                .put("event", "SUBSCRIBE")
                .done()
            .send();
    }

    public ControlMessageRequest openTaskSubscription(int topicId, String type)
    {
        return createControlMessageRequest()
            .messageType(ControlMessageType.ADD_TASK_SUBSCRIPTION)
            .data()
                .put("topicId", topicId)
                .put("taskType", type)
                .put("lockDuration", 1000L)
                .put("lockOwner", 0)
                .put("credits", 10)
                .done()
            .send();
    }

    public void closeChannel()
    {
        if (clientChannel == null)
        {
            throw new RuntimeException("No channel open");
        }

        clientChannel.close();
        clientChannel = null;
    }

    public void openChannel()
    {
        if (clientChannel != null)
        {
            throw new RuntimeException("Cannot open more than one channel at once");
        }

        clientChannel = transport
            .createClientChannel(new InetSocketAddress(host, port))
            .requestResponseProtocol(connectionPool)
            .transportChannelHandler(Protocols.FULL_DUPLEX_SINGLE_MESSAGE, subscribedEventCollector)
            .connect();
    }

}
