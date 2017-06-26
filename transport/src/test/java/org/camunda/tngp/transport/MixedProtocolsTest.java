package org.camunda.tngp.transport;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.requestresponse.client.PooledTransportRequest;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.singlemessage.DataFramePool;
import org.camunda.tngp.transport.singlemessage.OutgoingDataFrame;
import org.camunda.tngp.transport.spi.TransportChannelHandler;
import org.camunda.tngp.util.actor.ActorScheduler;
import org.camunda.tngp.util.actor.ActorSchedulerBuilder;
import org.junit.*;

public class MixedProtocolsTest
{
    private ActorScheduler actorScheduler;

    @Before
    public void setup()
    {
        actorScheduler = ActorSchedulerBuilder.createDefaultScheduler();
    }

    @After
    public void teardown()
    {
        actorScheduler.close();
    }

    @Test
    public void shouldEchoMessages()
    {

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        final CollectingHandler singleMessageHandler = new CollectingHandler();
        final int numRequests = 1000;

        try (Transport clientTransport = Transports.createTransport("client")
                .actorScheduler(actorScheduler)
                .build();
             Transport serverTransport = Transports.createTransport("server")
                .actorScheduler(actorScheduler)
                .build();

             ServerSocketBinding socketBinding = serverTransport.createServerSocketBinding(addr)
                   .transportChannelHandler(new ReverseOrderChannelHandler(serverTransport.getSendBuffer()))
                   .bind();

             TransportConnectionPool connectionPool = TransportConnectionPool.newFixedCapacityPool(clientTransport, 2, 64);

             TransportConnection connection = connectionPool.openConnection())
        {
            final Channel channel = clientTransport.createClientChannelPool()
                    .requestResponseProtocol(connectionPool)
                    .transportChannelHandler(Protocols.FULL_DUPLEX_SINGLE_MESSAGE, singleMessageHandler)
                    .build()
                    .requestChannel(addr);

            final DataFramePool dataFramePool = DataFramePool.newBoundedPool(2, clientTransport.getSendBuffer());

            for (int i = 0; i < numRequests; i++)
            {
                final PooledTransportRequest request = connection.openRequest(channel.getStreamId(), 1024);

                request.getClaimedRequestBuffer().putInt(request.getClaimedOffset(), i);
                request.commit();

                final OutgoingDataFrame dataFrame = dataFramePool.openFrame(channel.getStreamId(), 1024);
                dataFrame.getBuffer().putInt(0, i);
                dataFrame.commit();

                request.awaitResponse();
                assertThat(request.getResponseBuffer().getInt(0)).isEqualTo(i);
                request.close();
            }
        }
    }

    /**
     * Echos messages by copying to the send buffer, but inverts the order of
     * request-response messages and single messages. I.e. on a {@link Protocols#REQUEST_RESPONSE} messages,
     * it waits for the next {@link Protocols#FULL_DUPLEX_SINGLE_MESSAGE} messages, echos this message,
     * and only then echos the first message.
     */
    public static class ReverseOrderChannelHandler implements TransportChannelHandler
    {
        protected Dispatcher sendBuffer;

        protected UnsafeBuffer requestResponseMessageBuffer;
        protected int capacity;

        public ReverseOrderChannelHandler(Dispatcher sendBuffer)
        {
            this.sendBuffer = sendBuffer;
            this.requestResponseMessageBuffer = new UnsafeBuffer(new byte[1024 * 1024]);

        }

        @Override
        public void onChannelOpened(Channel transportChannel)
        {
        }

        @Override
        public void onChannelClosed(Channel transportChannel)
        {
        }

        @Override
        public void onChannelSendError(Channel transportChannel, DirectBuffer buffer, int offset, int length)
        {
        }

        @Override
        public boolean onChannelReceive(Channel transportChannel, DirectBuffer buffer, int offset, int length)
        {
            final short protocolId = buffer.getShort(offset);
            if (protocolId == Protocols.REQUEST_RESPONSE)
            {
                requestResponseMessageBuffer.putBytes(0, buffer, offset, length);
                capacity = length;
                return true;
            }
            else
            {
                final boolean success = echoMessage(transportChannel, buffer, offset, length);

                if (success)
                {
                    echoMessage(transportChannel, requestResponseMessageBuffer, 0, capacity);
                }

                return success;
            }
        }

        protected boolean echoMessage(Channel transportChannel, DirectBuffer buffer, int offset, int length)
        {
            final long offerPosition = sendBuffer.offer(buffer, offset, length, transportChannel.getStreamId());

            if (offerPosition < 0)
            {
                System.err.println("Could not echo message");
            }

            return offerPosition >= 0;
        }

        @Override
        public boolean onControlFrame(Channel transportChannel, DirectBuffer buffer, int offset, int length)
        {
            return true;
        }
    }


}
