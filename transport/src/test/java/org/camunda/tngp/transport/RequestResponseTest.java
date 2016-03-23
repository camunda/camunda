package org.camunda.tngp.transport;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.junit.Test;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class RequestResponseTest
{

    static class EchoServer extends Thread implements FragmentHandler
    {
        protected volatile boolean exit = false;

        protected final Dispatcher receiveBuffer;
        protected final Dispatcher sendBuffer;

        public EchoServer(Transport serverTransport)
        {
            receiveBuffer = serverTransport.getReceiveBuffer();
            sendBuffer = serverTransport.getSendBuffer();
        }

        @Override
        public void run()
        {
            while(!exit)
            {
                receiveBuffer.poll(this, Integer.MAX_VALUE);
            }
        }

        @Override
        public void onFragment(DirectBuffer buffer, int offset, int length, int streamId)
        {
            while(sendBuffer.offer(buffer, offset, length, streamId) < 0)
            {
                // spin
            }
        }

    }

    static class ClientFragmentHandler implements FragmentHandler
    {

        int lastMsgIdReceived;

        public ClientFragmentHandler()
        {
            reset();
        }

        public void onFragment(DirectBuffer buffer, int offset, int length, int streamId)
        {
            lastMsgIdReceived = buffer.getInt(offset);
        }

        public void reset()
        {
            lastMsgIdReceived = -1;
        }
    }

    @Test
    public void shouldEchoMessages() throws InterruptedException
    {
        // 1K message
        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));
        final ClientFragmentHandler fragmentHandler = new ClientFragmentHandler();
        final InetSocketAddress addr = new InetSocketAddress("localhost", 8080);

        final Transport clientTransport = Transports.createTransport("client")
            .thradingMode(ThreadingMode.SHARED)
            .build();

        final Transport serverTransport = Transports.createTransport("server")
            .thradingMode(ThreadingMode.SHARED)
            .build();

        serverTransport.createServerSocketBinding(addr)
            .bindSync();

        final EchoServer echoServer = new EchoServer(serverTransport);
        echoServer.start();

        ClientChannel channel = clientTransport.createClientChannel(addr)
            .connectSync();

        final Dispatcher sendBuffer = clientTransport.getSendBuffer();
        final Dispatcher receiveBuffer = clientTransport.getReceiveBuffer();

        for(int i = 0; i < 1000; i++)
        {
            msg.putInt(0, i);
            sendRequest(sendBuffer, msg, channel);
            waitForResponse(receiveBuffer, fragmentHandler, i);
        }

        channel.close();
        clientTransport.close();

        closeServer(serverTransport, echoServer);
    }

    protected void sendRequest(Dispatcher sendBuffer, UnsafeBuffer msg, ClientChannel channel)
    {
        while(sendBuffer.offer(msg, 0, msg.capacity(), channel.getId()) < 0)
        {
            // spin
        }
    }

    protected void waitForResponse(Dispatcher receiveBuffer, ClientFragmentHandler fragmentHandler, int msg)
    {
        while(fragmentHandler.lastMsgIdReceived != msg)
        {
            receiveBuffer.poll(fragmentHandler, 1);
        }
    }


    protected void closeServer(final Transport serverTransport, final EchoServer echoServer) throws InterruptedException
    {
        echoServer.exit = true;
        echoServer.join();
        serverTransport.close();
    }
}
