package org.camunda.tngp.transport;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.dispatcher.impl.Subscription;
import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.junit.Test;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ChannelRequestResponseTest
{

    static class ClientFragmentHandler implements FragmentHandler
    {

        int lastMsgIdReceived;

        ClientFragmentHandler()
        {
            reset();
        }

        public int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed)
        {
            lastMsgIdReceived = buffer.getInt(offset);
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
        final ClientFragmentHandler fragmentHandler = new ClientFragmentHandler();
        final InetSocketAddress addr = new InetSocketAddress("localhost", 51115);

        final Dispatcher clientReceiveBuffer = Dispatchers.create("client-receive-buffer")
                .bufferSize(16 * 1024 * 1024)
                .build();

        final Transport clientTransport = Transports.createTransport("client")
            .threadingMode(ThreadingMode.SHARED)
            .build();

        final Transport serverTransport = Transports.createTransport("server")
            .threadingMode(ThreadingMode.SHARED)
            .build();

        serverTransport.createServerSocketBinding(addr)
            // echo server: use send buffer as receive buffer
            .transportChannelHandler(new ReceiveBufferChannelHandler(serverTransport.getSendBuffer()))
            .bind();

        final ClientChannel channel = clientTransport.createClientChannel(addr)
            .transportChannelHandler(new ReceiveBufferChannelHandler(clientReceiveBuffer))
            .connect();

        final Dispatcher sendBuffer = clientTransport.getSendBuffer();
        final Subscription clientReceiveBufferSubscription = clientReceiveBuffer.openSubscription();

        for (int i = 0; i < 10000; i++)
        {
            msg.putInt(0, i);
            sendRequest(sendBuffer, msg, channel);
            waitForResponse(clientReceiveBufferSubscription, fragmentHandler, i);
        }

        channel.close();
        clientTransport.close();
        serverTransport.close();
    }

    protected void sendRequest(Dispatcher sendBuffer, UnsafeBuffer msg, ClientChannel channel)
    {
        while (sendBuffer.offer(msg, 0, msg.capacity(), channel.getId()) < 0)
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
