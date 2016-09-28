package org.camunda.tngp.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.singlemessage.DataFramePool;
import org.camunda.tngp.transport.singlemessage.DataFramePoolImpl;
import org.camunda.tngp.transport.singlemessage.OutgoingDataFrame;
import org.camunda.tngp.transport.spi.TransportChannelHandler;
import org.junit.Test;

public class ProtocolSingleMessageTest
{
    @Test
    public void shouldEchoMessages() throws Exception
    {
        final InetSocketAddress addr = new InetSocketAddress("localhost", 51115);
        final CollectingHandler clientChannelHandler = new CollectingHandler();
        final int numRequests = 1000000;

        try (
            final Transport clientTransport = Transports.createTransport("client").build();
            final Transport serverTransport = Transports.createTransport("server").build();

            final ServerSocketBinding socketBinding = serverTransport.createServerSocketBinding(addr)
                // echo server: use send buffer as receive buffer
                .transportChannelHandler(new ReceiveBufferChannelHandler(serverTransport.getSendBuffer()))
                .bind();

            final TransportConnectionPool connectionPool = TransportConnectionPool.newFixedCapacityPool(clientTransport, 2, 64);

            final ClientChannel channel = clientTransport
                 .createClientChannel(addr)
                 .transportChannelHandler(Protocols.FULL_DUPLEX_SINGLE_MESSAGE, clientChannelHandler)
                 .connect())
        {

            final DataFramePool dataFramePool = new DataFramePoolImpl(32, clientTransport.getSendBuffer());


            for (int i = 0; i < numRequests; i++)
            {
                OutgoingDataFrame dataFrame;
                do
                {
                    dataFrame = dataFramePool.openFrame(BitUtil.SIZE_OF_INT, channel.getId());
                }
                while (dataFrame == null);

                dataFrame.getBuffer().putInt(0, i);
                dataFrame.commit();
                dataFrame.close();
            }

            while (!(clientChannelHandler.messagesReceived == numRequests))
            {

            }
        }

        assertThat(clientChannelHandler.messagesReceived).isEqualTo(numRequests);
    }

    protected static class CollectingHandler implements TransportChannelHandler
    {

        protected volatile int messagesReceived = 0;
        protected int lastMessageId = -1;

        @Override
        public void onChannelOpened(TransportChannel transportChannel)
        {
        }

        @Override
        public void onChannelClosed(TransportChannel transportChannel)
        {
        }

        @Override
        public void onChannelSendError(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
        {
        }

        @Override
        public boolean onChannelReceive(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
        {
            final int messageId = buffer.getInt(offset + TransportHeaderDescriptor.headerLength());
            assertThat(messageId - 1).isEqualTo(lastMessageId);
            lastMessageId++;
            messagesReceived++;
            return true;
        }

        @Override
        public void onControlFrame(TransportChannel transportChannel, DirectBuffer buffer, int offset, int length)
        {
        }

    }
}
