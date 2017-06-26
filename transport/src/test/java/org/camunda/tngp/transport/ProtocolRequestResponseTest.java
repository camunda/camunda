package org.camunda.tngp.transport;

import org.camunda.tngp.transport.requestresponse.client.RequestQueue;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.transport.requestresponse.client.TransportRequest;
import org.camunda.tngp.util.actor.ActorScheduler;
import org.camunda.tngp.util.actor.ActorSchedulerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ProtocolRequestResponseTest
{
    private ActorScheduler actorScheduler;

    @Before
    public void setup()
    {
        actorScheduler = ActorSchedulerBuilder.createDefaultScheduler();
    }

    @After
    public void teardown() throws Exception
    {
        actorScheduler.close();
    }

    @Test
    public void shouldEchoMessages() throws Exception
    {
        final SocketAddress addr = new SocketAddress("localhost", 51115);

        try (Transport clientTransport = Transports.createTransport("client").actorScheduler(actorScheduler).build();
             Transport serverTransport = Transports.createTransport("server").actorScheduler(actorScheduler).build();

             ServerSocketBinding socketBinding = serverTransport.createServerSocketBinding(addr)
                // echo server: use send buffer as receive buffer
                .transportChannelHandler(new ReceiveBufferChannelHandler(serverTransport.getSendBuffer()))
                .bind();

             TransportConnectionPool connectionPool = TransportConnectionPool.newFixedCapacityPool(clientTransport, 2, 64);
             TransportConnection connection = connectionPool.openConnection())
        {

            final Channel channel = clientTransport
                    .createClientChannelPool()
                    .requestResponseProtocol(connectionPool)
                    .build()
                    .requestChannel(addr);

            final RequestQueue q = new RequestQueue(32);

            int completedRequests = 0;

            do
            {
                if (q.hasCapacity())
                {
                    final TransportRequest request = connection.openRequest(channel.getStreamId(), 1024);
                    if (request != null)
                    {
                        request.commit();
                        q.offer(request);
                    }
                }

                TransportRequest request = null;
                while ((request = q.pollNextResponse()) != null)
                {
                    request.close();
                    ++completedRequests;
                }
            }
            while (completedRequests < 100_000);

            q.closeAll();
        }
    }
}
