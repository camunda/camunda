package org.camunda.tngp.transport;

import java.net.InetSocketAddress;

import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.camunda.tngp.transport.requestresponse.client.RequestQueue;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.requestresponse.client.TransportRequest;
import org.junit.Test;

public class ProtocolRequestResponseTest
{
    @Test
    public void shouldEchoMessages() throws Exception
    {
        final InetSocketAddress addr = new InetSocketAddress("localhost", 8080);

        try (final Transport clientTransport = Transports.createTransport("client").build();
             final Transport serverTransport = Transports.createTransport("server").build();

             final ServerSocketBinding socketBinding = serverTransport.createServerSocketBinding(addr)
                // echo server: use send buffer as receive buffer
                .transportChannelHandler(new ReceiveBufferChannelHandler(serverTransport.getSendBuffer()))
                .bind();

             final TransportConnectionPool connectionPool = TransportConnectionPool.newFixedCapacityPool(clientTransport, 2, 64);
             final ClientChannel channel = clientTransport.createClientChannel(addr).requestResponseChannel(connectionPool).connect();
             final TransportConnection connection = connectionPool.openConnection())
        {
            final RequestQueue q = new RequestQueue(32);

            int completedRequests = 0;

            do
            {
                if(q.hasCapacity())
                {
                    final TransportRequest request = connection.openRequest(channel.getId(), 1024);
                    if(request != null)
                    {
                        request.commit();
                        q.offer(request);
                    }
                }

                TransportRequest request = null;
                while((request = q.pollNextResponse()) != null)
                {
                    request.close();
                    ++completedRequests;
                }
            }
            while(completedRequests < 1000000);

            q.closeAll();
        }
    }
}
