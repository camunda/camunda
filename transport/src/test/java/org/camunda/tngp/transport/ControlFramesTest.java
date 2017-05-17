package org.camunda.tngp.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.typeOffset;
import static org.camunda.tngp.transport.impl.TransportControlFrameDescriptor.TYPE_PROTO_CONTROL_FRAME;

import java.nio.ByteBuffer;
import java.util.Date;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.spi.TransportChannelHandler;
import org.camunda.tngp.util.time.ClockUtil;
import org.junit.Test;

public class ControlFramesTest
{

    protected static final DirectBuffer FULL_DUPLEX_CONTROL_FRAME;

    static
    {
        final UnsafeBuffer writer = new UnsafeBuffer(ByteBuffer.allocate(alignedLength(TransportHeaderDescriptor.HEADER_LENGTH)));

        writer.putInt(lengthOffset(0), 0);
        writer.putShort(typeOffset(0), TYPE_PROTO_CONTROL_FRAME);
        writer.putShort(TransportHeaderDescriptor.protocolIdOffset(DataFrameDescriptor.HEADER_LENGTH), Protocols.FULL_DUPLEX_SINGLE_MESSAGE);

        FULL_DUPLEX_CONTROL_FRAME = writer;
    }

    /**
     * Tests sending control frames. Frames are echoed from the server to reproduce a bug
     * where the sender agents gets stuck in an infinite loop, because the receiver agent
     * runs in the same thread and is required to read from the socket channel before
     * any more can be written
     */
    @Test
    public void shouldEchoControlFrames()
    {
        // fix the time to disable keep alive messages
        ClockUtil.setCurrentTime(new Date().getTime());

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        final CollectingHandler collectingHandler = new CollectingHandler();
        final int numRequests = 300_000;

        try (
            final Transport clientTransport = Transports.createTransport("client").build();
            final Transport serverTransport = Transports.createTransport("server").build();

            final ServerSocketBinding socketBinding = serverTransport.createServerSocketBinding(addr)
                .transportChannelHandler(new EchoControlFramesHandler())
                .bind();

            final TransportConnectionPool connectionPool = TransportConnectionPool.newFixedCapacityPool(clientTransport, 2, 64);

            )
        {
            final Channel channel = clientTransport
                .createClientChannelPool()
                .transportChannelHandler(Protocols.FULL_DUPLEX_SINGLE_MESSAGE, collectingHandler)
                .build()
                .requestChannel(addr);

            for (int i = 0; i < numRequests; i++)
            {
                while (!channel.scheduleControlFrame(FULL_DUPLEX_CONTROL_FRAME))
                {
                }
            }

            while (collectingHandler.getControlFramesReceived() < numRequests)
            {
            }
        }

        assertThat(collectingHandler.getControlFramesReceived()).isEqualTo(numRequests);
    }

    protected static class EchoControlFramesHandler implements TransportChannelHandler
    {

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
            return true;
        }

        protected int loopCounter = 0;

        @Override
        public boolean onControlFrame(Channel transportChannel, DirectBuffer buffer, int offset, int length)
        {
            return transportChannel.scheduleControlFrame(buffer, offset, length);
        }

    }


}
