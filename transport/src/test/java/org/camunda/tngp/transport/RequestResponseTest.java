package org.camunda.tngp.transport;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.camunda.tngp.dispatcher.FragmentHandler;
import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.junit.Test;

import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class RequestResponseTest
{

    static class EchoServer extends Thread implements FragmentHandler
    {

        protected volatile boolean exit = false;

        protected Transport transport;

        public EchoServer(Transport serverTransport)
        {
            this.transport = serverTransport;
        }

        @Override
        public void run()
        {
            while(!exit)
            {
                transport.poll(this, 10);
            }
        }

        @Override
        public void onFragment(DirectBuffer buffer, int offset, int length, int streamId)
        {
            while(transport.send(streamId, buffer, offset, length) < 0)
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
        final UnsafeBuffer msg = new UnsafeBuffer(ByteBuffer.allocateDirect(BitUtil.SIZE_OF_INT));
        final InetSocketAddress addr = new InetSocketAddress("localhost", 8080);

        final Transport clientTransport = Transports.createTransport("client")
            .thradingMode(ThreadingMode.SHARED)
            .build();

        final Transport serverTransport = Transports.createTransport("server")
                .thradingMode(ThreadingMode.SHARED)
                .build();

        final EchoServer echoServer = new EchoServer(serverTransport);

        serverTransport.createServerSocketBinding(addr)
            .bindSync();

        echoServer.start();

        ClientChannel channel = clientTransport.createClientChannel(addr)
            .connectSync();

        final ClientFragmentHandler fragmentHandler = new ClientFragmentHandler();
        for(int i = 0; i < 100; i++)
        {
            msg.putInt(0, i);
            sendRequest(clientTransport, msg, channel);
            waitForResponse(clientTransport, fragmentHandler, i);
        }

        channel.close();
        clientTransport.close();

        closeServer(serverTransport, echoServer);

    }

    protected void waitForResponse(Transport transport, ClientFragmentHandler fragmentHandler, int msg)
    {
        while(fragmentHandler.lastMsgIdReceived != msg)
        {
            transport.poll(fragmentHandler, 1);
        }
    }

    private void sendRequest(Transport clientTransport, UnsafeBuffer msg, ClientChannel channel)
    {
        while(clientTransport.send(channel, msg, 0, msg.capacity()) < 0)
        {
            // spin
        }
    }

    private void closeServer(final Transport serverTransport, final EchoServer echoServer) throws InterruptedException
    {
        echoServer.exit = true;
        echoServer.join();
        serverTransport.close();
    }

}
