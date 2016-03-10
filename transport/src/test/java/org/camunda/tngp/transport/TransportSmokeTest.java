package org.camunda.tngp.transport;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;

import org.camunda.tngp.transport.ClientChannel;
import org.camunda.tngp.transport.Transport;
import org.camunda.tngp.transport.Transports;
import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class TransportSmokeTest
{

    @Test
    public void shoulEchoMessage() throws InterruptedException
    {
        final InetSocketAddress addr = new InetSocketAddress("localhost", 8080);

        final int totalWork = 100000000;
        final CountDownLatch latch = new CountDownLatch(totalWork);

        final Transport client = Transports.createTransport("default")
            .thradingMode(ThreadingMode.DEDICATED)
            .build();

        final Transport server = Transports.createTransport("default")
                .thradingMode(ThreadingMode.DEDICATED)
                .build();

        server.createServerSocketBinding(addr)
            .bindSync();

        ClientChannel channel = client.createClientChannel(addr)
            .connectSync();

        UnsafeBuffer unsafeBuffer = new UnsafeBuffer(ByteBuffer.allocate(10*1024));

        for(int i = 0; i < totalWork; i++)
        {
            unsafeBuffer.putInt(0, i);
            while(channel.offer(unsafeBuffer, 0 ,unsafeBuffer.capacity()) < 0)
            {
                // spin
            }
        }

        latch.await();

        channel.closeSync();

        client.close();
    }

}
