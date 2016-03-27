package org.camunda.tngp.transport;

import java.net.InetSocketAddress;

import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.dispatcher.Dispatchers;
import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.camunda.tngp.transport.protocol.client.RequestResponseChannelHandler;
import org.camunda.tngp.transport.protocol.client.TransportConnection;
import org.camunda.tngp.transport.protocol.client.TransportConnectionManager;
import org.camunda.tngp.transport.protocol.client.TransportRequest;
import org.camunda.tngp.transport.protocol.client.TransportRequestImpl;
import org.junit.Test;

import uk.co.real_logic.agrona.MutableDirectBuffer;

public class ProtocolRequestResponseTest
{

    @Test
    public void shouldEchoMessages() throws Exception
    {
        // 1K message
        final InetSocketAddress addr = new InetSocketAddress("localhost", 8080);

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

        int concurrentRequests = 16;
        TransportConnectionManager connectionManager = new TransportConnectionManager(clientTransport, 2, concurrentRequests);
        RequestResponseChannelHandler channelHandler = new RequestResponseChannelHandler(connectionManager);

        ClientChannel channel = clientTransport.createClientChannel(addr)
            .transportChannelHandler(channelHandler)
            .connect();

        TransportConnection connection = connectionManager.openConnection();

        TransportRequest[] requestPool = new TransportRequest[concurrentRequests];
        for (int i = 0; i < requestPool.length; i++)
        {
            requestPool[i] = new TransportRequestImpl(1024, 5000);
        }

        for (int i = 0; i < 10000; i++)
        {
            for (int j = 0; j < concurrentRequests; j++)
            {
                TransportRequest request = requestPool[j];
                if (connection.openRequest(request, channel.getId(), 1024))
                {
                    MutableDirectBuffer buffer = request.getClaimedRequestBuffer();
                    buffer.putInt(request.getClaimedOffset(), i);
                    request.commit();
                }
            }

            for (int j = 0; j < concurrentRequests; j++)
            {
                TransportRequest request = requestPool[j];
                if(request.isOpen())
                {
                    request.awaitResponse();
                }
                request.close();
            }
        }

        connectionManager.closeAll();
        clientTransport.close();
        serverTransport.close();
    }
}
