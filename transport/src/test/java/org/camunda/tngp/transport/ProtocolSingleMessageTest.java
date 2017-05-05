package org.camunda.tngp.transport;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.BitUtil;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.singlemessage.DataFramePool;
import org.camunda.tngp.transport.singlemessage.DataFramePoolImpl;
import org.camunda.tngp.transport.singlemessage.OutgoingDataFrame;
import org.junit.Test;

public class ProtocolSingleMessageTest
{
    @Test
    public void shouldEchoMessages() throws Exception
    {
        final SocketAddress addr = new SocketAddress("localhost", 51115);
        final CollectingHandler clientChannelHandler = new CollectingHandler();
        final int numRequests = 1_000_000;

        try (
            final Transport clientTransport = Transports.createTransport("client").build();
            final Transport serverTransport = Transports.createTransport("server").build();

            final ServerSocketBinding socketBinding = serverTransport.createServerSocketBinding(addr)
                // echo server: use send buffer as receive buffer
                .transportChannelHandler(new ReceiveBufferChannelHandler(serverTransport.getSendBuffer()))
                .bind();

            final TransportConnectionPool connectionPool = TransportConnectionPool.newFixedCapacityPool(clientTransport, 2, 64);

            final ClientChannel channel = clientTransport
                    .createClientChannelPool()
                    .transportChannelHandler(Protocols.FULL_DUPLEX_SINGLE_MESSAGE, clientChannelHandler)
                    .build()
                    .requestChannel(addr))
        {
            final DataFramePool dataFramePool = new DataFramePoolImpl(32, clientTransport.getSendBuffer());

            for (int i = 0; i < numRequests; i++)
            {
                OutgoingDataFrame dataFrame;
                do
                {
                    dataFrame = dataFramePool.openFrame(channel.getId(), BitUtil.SIZE_OF_INT);
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



    // TODO: dieser und die anderen Tests hängen manchmal; hat eventuell damit zu tun, dass Control Frames nicht richtig (zum richtigen Zeitpunkt)
    // geschrieben werden und deshalb nicht die erwünschte Zahl der Responses eintrifft
}
