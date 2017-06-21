package org.camunda.tngp.transport;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.Subscription;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.util.actor.ActorScheduler;
import org.camunda.tngp.util.actor.ActorSchedulerImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ChannelRequestResponseTest
{

    private ActorScheduler actorScheduler;

    @Before
    public void setup()
    {
        actorScheduler = ActorSchedulerImpl.createDefaultScheduler();
    }

    @After
    public void teardown()
    {
        actorScheduler.close();
    }

    static class ClientFragmentHandler implements FragmentHandler
    {

        int lastMsgIdReceived;

        ClientFragmentHandler()
        {
            reset();
        }

        @Override
        public int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed)
        {
            lastMsgIdReceived = buffer.getInt(offset + TransportHeaderDescriptor.HEADER_LENGTH);
            return FragmentHandler.CONSUME_FRAGMENT_RESULT;
        }

        public void reset()
        {
            lastMsgIdReceived = -1;
        }
    }

    @Test
    public void shouldEchoMessages() throws Exception
    {
        // 1K message
        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
        new TransportHeaderDescriptor().wrap(msg, 0)
            .protocolId(Protocols.REQUEST_RESPONSE);

        final ClientFragmentHandler fragmentHandler = new ClientFragmentHandler();
        final SocketAddress addr = new SocketAddress("localhost", 51115);

        final Dispatcher clientReceiveBuffer = Dispatchers.create("client-receive-buffer")
                .actorScheduler(actorScheduler)
                .bufferSize(16 * 1024 * 1024)
                .build();

        final Transport clientTransport = Transports.createTransport("client")
            .actorScheduler(actorScheduler)
            .build();

        final Transport serverTransport = Transports.createTransport("server")
            .actorScheduler(actorScheduler)
            .build();

        serverTransport.createServerSocketBinding(addr)
            // echo server: use send buffer as receive buffer
            .transportChannelHandler(new ReceiveBufferChannelHandler(serverTransport.getSendBuffer()))
            .bind();

        final ChannelManager channelManager = clientTransport.createClientChannelPool()
            .transportChannelHandler(Protocols.REQUEST_RESPONSE, new ReceiveBufferChannelHandler(clientReceiveBuffer))
            .build();
        final Channel channel = channelManager.requestChannel(addr);

        final Dispatcher sendBuffer = clientTransport.getSendBuffer();
        final Subscription clientReceiveBufferSubscription = clientReceiveBuffer.openSubscription("client-receiver");

        for (int i = 0; i < 10000; i++)
        {
            msg.putInt(TransportHeaderDescriptor.headerLength(), i);
            sendRequest(sendBuffer, msg, channel);
            waitForResponse(clientReceiveBufferSubscription, fragmentHandler, i);
        }

        channelManager.returnChannel(channel);
        clientTransport.close();
        serverTransport.close();
    }

    protected void sendRequest(Dispatcher sendBuffer, UnsafeBuffer msg, Channel channel)
    {
        while (sendBuffer.offer(msg, 0, msg.capacity(), channel.getStreamId()) < 0)
        {
            // spin
        }
    }

    protected void waitForResponse(Subscription clientReceiveBufferSubscription, ClientFragmentHandler fragmentHandler, int msg)
    {
        while (fragmentHandler.lastMsgIdReceived != msg)
        {
            clientReceiveBufferSubscription.poll(fragmentHandler, 1);
        }
    }

}
