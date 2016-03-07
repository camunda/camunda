package net.long_running.transport;

import java.net.InetSocketAddress;

import org.junit.Test;

import net.long_running.transport.TransportBuilder.ThreadingMode;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class TransportSmokeTest
{

    @Test
    public void shoulEchoMessage() throws InterruptedException
    {
        final InetSocketAddress addr = new InetSocketAddress("localhost", 8080);

        final Transport transport = Transports.createTransport("default")
            .thradingMode(ThreadingMode.DEDICATED)
            .build();

        transport.createServerSocketBinding(addr)
            .serverChannelHandler((ch) ->
            {
                ch.setChannelFrameHandler((buffer, offset, length) ->
                {
                    // send message back to client
                    ch.offer(buffer, offset, length);
                });
            })
            .bindSync();

        ClientChannel channel = transport.createClientChannel(addr)
            .channelFrameHandler((buffer, offset, length) ->
            {
                byte[] bytes = new byte[length];
                buffer.getBytes(offset, bytes);
                System.out.println(new String(bytes));
            })
            .connectSync();

        UnsafeBuffer unsafeBuffer = new UnsafeBuffer("hello".getBytes());
        channel.offer(unsafeBuffer, 0 , unsafeBuffer.capacity());

        transport.close();
    }

}
