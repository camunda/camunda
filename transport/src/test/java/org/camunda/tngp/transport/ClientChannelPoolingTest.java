package org.camunda.tngp.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Date;

import org.camunda.tngp.test.util.TestUtil;
import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.camunda.tngp.transport.impl.ClientChannelPoolImpl;
import org.camunda.tngp.util.time.ClockUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ClientChannelPoolingTest
{

    protected Transport clientTransport;
    protected Transport serverTransport;

    @Before
    public void setUp()
    {
        clientTransport = Transports.createTransport("client")
            .threadingMode(ThreadingMode.SHARED)
            .build();

        serverTransport = Transports.createTransport("server")
                .threadingMode(ThreadingMode.SHARED)
                .build();
    }

    @After
    public void tearDown()
    {
        ClockUtil.reset();
        clientTransport.close();
        serverTransport.close();
    }

    @Test
    public void shouldServeClientChannel()
    {
        // given
        final ClientChannelPool pool = clientTransport.createClientChannelPool().build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        // when
        final ClientChannel channel = pool.requestChannel(addr);

        // then
        assertThat(channel.isOpen()).isTrue();
        assertThat(channel.getRemoteAddress()).isEqualTo(addr);
    }

    @Test
    public void shouldServeClientChannelAsync()
    {
        // given
        final ClientChannelPool pool = clientTransport.createClientChannelPool().build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        // when
        final PooledFuture<ClientChannel> channelFuture = pool.requestChannelAsync(addr);

        // then
        final ClientChannel channel = TestUtil
                .doRepeatedly(() -> channelFuture.pollAndReturnOnSuccess())
                .until(c -> c != null);
        assertThat(channel.isOpen()).isTrue();
        assertThat(channel.getRemoteAddress()).isEqualTo(addr);
    }

    @Test
    public void shouldReuseClientChannelsToSameRemoteAddress()
    {
        // given
        final ClientChannelPool pool = clientTransport.createClientChannelPool().build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        final ClientChannel channel1 = pool.requestChannel(addr);

        // when
        final ClientChannel channel2 = pool.requestChannel(addr);

        // then
        assertThat(channel2).isSameAs(channel1);
    }

    @Test
    public void shouldNotReuseClientChannelsToDifferentRemoteAddress()
    {
        // given
        final ClientChannelPool pool = clientTransport.createClientChannelPool().build();

        final SocketAddress addr1 = new SocketAddress("localhost", 51115);
        final SocketAddress addr2 = new SocketAddress("localhost", 51116);
        serverTransport.createServerSocketBinding(addr1).bind();
        serverTransport.createServerSocketBinding(addr2).bind();

        final ClientChannel channel1 = pool.requestChannel(addr1);

        // when
        final ClientChannel channel2 = pool.requestChannel(addr2);

        // then
        assertThat(channel2).isNotSameAs(channel1);
        assertThat(channel2.getId()).isNotEqualTo(channel1.getId());
    }

    @Test
    public void shouldOpenNewChannelAfterChannelClose()
    {
        // given
        final ClientChannelPool pool = clientTransport.createClientChannelPool().build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        final ClientChannel channel1 = pool.requestChannel(addr);
        channel1.close();

        // when
        final ClientChannel channel2 = pool.requestChannel(addr);

        // then
        assertThat(channel2).isNotSameAs(channel1);
        assertThat(channel2.getId()).isNotEqualTo(channel1.getId());
    }

    @Test
    public void shouldCloseChannelsOnPoolClose()
    {
        // given
        final ClientChannelPoolImpl pool = (ClientChannelPoolImpl) clientTransport.createClientChannelPool().build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        final ClientChannel channel = pool.requestChannel(addr);

        // when
        pool.closeAsync().join();

        // then
        assertThat(channel.isClosed()).isTrue();
    }

    @Test
    public void shouldEvictUnusedChannelWhenCapacityIsReached()
    {
        // given
        ClockUtil.setCurrentTime(new Date().getTime());

        final int initialCapacity = 2;

        final ClientChannelPool pool = clientTransport
                .createClientChannelPool()
                .initialCapacity(initialCapacity)
                .build();

        bindServerSocketsInPortRange(51115, initialCapacity + 1);
        final ClientChannel[] channels = openChannelsInPortRange(pool, 51115, initialCapacity);

        pool.returnChannel(channels[1]);
        ClockUtil.addTime(Duration.ofHours(1));
        pool.returnChannel(channels[0]);

        // when
        final ClientChannel newChannel = pool.requestChannel(new SocketAddress("localhost", 51115 + initialCapacity));

        // then
        // there is no object reuse
        assertThat(channels).doesNotContain(newChannel);

        // the least recently returned channel has been closed asynchronously
        TestUtil.waitUntil(() -> channels[1].isClosed());
        assertThat(channels[1].isClosed()).isTrue();
        assertThat(channels[0].isOpen()).isTrue();
    }

    @Test
    public void shouldGrowPoolWhenCapacityIsExceeded()
    {
        final int initialCapacity = 2;

        final ClientChannelPool pool = clientTransport
                .createClientChannelPool()
                .initialCapacity(initialCapacity)
                .build();

        bindServerSocketsInPortRange(51115, initialCapacity + 1);

        // when
        final ClientChannel[] channels = openChannelsInPortRange(pool, 51115, initialCapacity + 1);

        // then all channels are open
        assertThat(channels).hasSize(initialCapacity + 1);
        for (ClientChannel channel : channels)
        {
            assertThat(channel.isOpen()).isTrue();
        }
    }


    protected ClientChannel[] openChannelsInPortRange(ClientChannelPool pool, int firstPort, int range)
    {
        final ClientChannel[] channels = new ClientChannel[range];
        for (int i = 0; i < range; i++)
        {
            channels[i] = pool.requestChannel(new SocketAddress("localhost", firstPort + i));
        }
        return channels;
    }

    protected void bindServerSocketsInPortRange(int firstPort, int range)
    {
        for (int i = 0; i < range + 1; i++)
        {
            serverTransport.createServerSocketBinding(new SocketAddress("localhost", firstPort + i)).bind();
        }
    }
}
