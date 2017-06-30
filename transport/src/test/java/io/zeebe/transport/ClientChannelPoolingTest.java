package io.zeebe.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.CompletionException;

import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.impl.ChannelImpl;
import io.zeebe.transport.impl.ServerSocketBindingImpl;
import io.zeebe.transport.protocol.Protocols;
import io.zeebe.transport.singlemessage.DataFramePool;
import io.zeebe.transport.util.RecordingChannelHandler;
import io.zeebe.transport.util.RecordingChannelListener;
import io.zeebe.util.PooledFuture;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import io.zeebe.util.time.ClockUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ClientChannelPoolingTest
{

    @Rule
    public ExpectedException exception = ExpectedException.none();

    protected Transport clientTransport;
    protected Transport serverTransport;

    private ActorScheduler actorScheduler;

    @Before
    public void setUp()
    {
        actorScheduler = ActorSchedulerBuilder.createDefaultScheduler();

        clientTransport = Transports.createTransport("client")
            .actorScheduler(actorScheduler)
            .channelKeepAlivePeriod(Integer.MAX_VALUE)
            .build();

        serverTransport = Transports.createTransport("server")
                .actorScheduler(actorScheduler)
                .build();
    }

    @After
    public void tearDown()
    {
        ClockUtil.reset();
        clientTransport.close();
        serverTransport.close();
        actorScheduler.close();
    }

    @Test
    public void shouldServeClientChannel()
    {
        // given
        final ChannelManager channelManager = clientTransport.createClientChannelPool().build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        // when
        final Channel channel = channelManager.requestChannel(addr);

        // then
        assertThat(channel.isReady()).isTrue();
        assertThat(channel.getRemoteAddress()).isEqualTo(addr);
    }

    @Test
    public void shouldServeClientChannelAsync()
    {
        // given
        final ChannelManager channelManager = clientTransport.createClientChannelPool().build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        // when
        final PooledFuture<Channel> channelFuture = channelManager.requestChannelAsync(addr);

        // then
        final Channel channel = TestUtil
                .doRepeatedly(() -> channelFuture.poll())
                .until(c -> c != null);
        assertThat(channelFuture.isFailed()).isFalse();

        assertThat(channel.isReady()).isTrue();
        assertThat(channel.getRemoteAddress()).isEqualTo(addr);
    }

    @Test
    public void shouldReuseClientChannelsToSameRemoteAddress()
    {
        // given
        final ChannelManager channelManager = clientTransport.createClientChannelPool().build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        final Channel stream1 = channelManager.requestChannel(addr);

        // when
        final Channel stream2 = channelManager.requestChannel(addr);

        // then
        assertThat(stream1).isSameAs(stream2);
    }

    @Test
    public void shouldNotReuseStreamsToDifferentRemoteAddress()
    {
        // given
        final ChannelManager channelManager = clientTransport.createClientChannelPool().build();

        final SocketAddress addr1 = new SocketAddress("localhost", 51115);
        final SocketAddress addr2 = new SocketAddress("localhost", 51116);
        serverTransport.createServerSocketBinding(addr1).bind();
        serverTransport.createServerSocketBinding(addr2).bind();

        final Channel stream1 = channelManager.requestChannel(addr1);

        // when
        final Channel stream2 = channelManager.requestChannel(addr2);

        // then
        assertThat(stream2).isNotSameAs(stream1);
        assertThat(stream2.getStreamId()).isNotEqualTo(stream1.getStreamId());
    }

    @Test
    public void shouldOpenNewChannelAfterChannelClose()
    {
        // given
        final ChannelManager channelManager = clientTransport.createClientChannelPool().build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        final Channel channel1 = channelManager.requestChannel(addr);
        ((ChannelImpl) channel1).initiateClose();
        TestUtil.waitUntil(() -> channel1.isClosed());

        // when
        final Channel channel2 = channelManager.requestChannel(addr);

        // then
        assertThat(channel2).isNotSameAs(channel1);
        assertThat(channel2.getStreamId()).isNotEqualTo(channel1.getStreamId());
    }

    @Test
    public void shouldCloseChannelsOnPoolClose()
    {
        // given
        final ChannelManager channelManager = clientTransport.createClientChannelPool().build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        final Channel channel = channelManager.requestChannel(addr);

        // when
        channelManager.closeAllChannelsAsync().join();

        // then
        assertThat(channel.isClosed()).isTrue();
    }

    @Test
    public void shouldEvictUnusedStreamWhenCapacityIsReached()
    {
        // given
        ClockUtil.setCurrentTime(new Date().getTime());

        final int initialCapacity = 2;

        final ChannelManager channelManager = clientTransport
                .createClientChannelPool()
                .initialCapacity(initialCapacity)
                .build();

        bindServerSocketsInPortRange(51115, initialCapacity + 1);
        final Channel[] channels = openStreamsInPortRange(channelManager, 51115, initialCapacity);

        channelManager.returnChannel(channels[1]);
        ClockUtil.addTime(Duration.ofHours(1));
        channelManager.returnChannel(channels[0]);

        // when
        final Channel newChannel = channelManager.requestChannel(new SocketAddress("localhost", 51115 + initialCapacity));

        // then
        // there is no object reuse
        assertThat(channels).doesNotContain(newChannel);

        // the least recently returned channel has been closed asynchronously
        TestUtil.waitUntil(() -> channels[1].isClosed());
        assertThat(channels[1].isClosed()).isTrue();
        assertThat(channels[0].isReady()).isTrue();
    }

    @Test
    public void shouldGrowPoolWhenCapacityIsExceeded()
    {
        final int initialCapacity = 2;

        final ChannelManager channelManager = clientTransport
                .createClientChannelPool()
                .initialCapacity(initialCapacity)
                .build();

        bindServerSocketsInPortRange(51115, initialCapacity + 1);

        // when
        final Channel[] channels = openStreamsInPortRange(channelManager, 51115, initialCapacity + 1);

        // then all channels are open
        assertThat(channels).hasSize(initialCapacity + 1);
        for (Channel channel : channels)
        {
            assertThat(channel.isReady()).isTrue();
        }
    }

    @Test
    public void shouldFailToServeAsyncWhenChannelConnectFails()
    {
        // given
        final ChannelManager channelManager = clientTransport.createClientChannelPool().build();
        final SocketAddress addr = new SocketAddress("localhost", 51115);

        // when
        final PooledFuture<Channel> future = channelManager.requestChannelAsync(addr);

        // then
        TestUtil.waitUntil(() -> future.isFailed());

        assertThat(future.isFailed()).isTrue();
        assertThat(future.poll()).isNull();
    }

    @Test
    public void shouldFailToServeWhenChannelConnectFails()
    {
        // given
        final ChannelManager channelManager = clientTransport.createClientChannelPool().build();
        final SocketAddress addr = new SocketAddress("localhost", 51115);

        // then
        exception.expect(CompletionException.class);

        // when
        channelManager.requestChannel(addr);
    }

    @Test
    public void shouldAllowNullValuesOnReturnChannel()
    {
        // given
        final ChannelManager channelManager = clientTransport.createClientChannelPool().build();

        // when
        try
        {
            channelManager.returnChannel(null);
            // then there is no exception
        }
        catch (Exception e)
        {
            fail("should not throw exception");
        }
    }

    @Test
    public void shouldResetFailedConnectFuturesOnRelease()
    {
        // given
        final ChannelManager channelManager = clientTransport
                .createClientChannelPool()
                .build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        final PooledFuture<Channel> future = channelManager.requestChannelAsync(addr);

        TestUtil.waitUntil(() -> future.isFailed());

        // when
        future.release();

        // then
        assertThat(future.poll()).isNull();
        assertThat(future.isFailed()).isFalse();
    }

    @Test
    public void shouldResetSuccessfulConnectFuturesOnRelease()
    {
        // given
        final ChannelManager channelManager = clientTransport
                .createClientChannelPool()
                .build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        final PooledFuture<Channel> future = channelManager.requestChannelAsync(addr);
        TestUtil.waitUntil(() -> future.poll() != null);

        // when
        future.release();

        // then
        assertThat(future.poll()).isNull();
        assertThat(future.isFailed()).isFalse();
    }

    @Test
    public void shouldFailConcurrentRequestsForSameRemoteAddress()
    {
        final ChannelManager channelManager = clientTransport
                .createClientChannelPool()
                .build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);

        // when
        final PooledFuture<Channel> future1 = channelManager.requestChannelAsync(addr);
        final PooledFuture<Channel> future2 = channelManager.requestChannelAsync(addr);
        TestUtil.waitUntil(() -> future1.isFailed() && future2.isFailed());

        // then
        assertThat(future1.isFailed()).isTrue();
        assertThat(future2.isFailed()).isTrue();
    }

    /**
     * Reproducing a deadlock bug when a channel is attempted to be reopened while the transport is closing down
     */
    @Test
    public void shouldCloseTransportWithFailingChannel() throws IOException, InterruptedException
    {
        // given
        final ChannelManager channelManager = clientTransport
                .createClientChannelPool()
                .build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        final Channel channel = channelManager.requestChannel(addr);
        ((ChannelImpl) channel).getSocketChannel().shutdownInput();

        // when
        clientTransport.close();

        // then
        assertThat(channel.isClosed()).isTrue();
    }

    @Test
    public void shouldReopenChannelOnInterruption() throws IOException
    {
        // given
        final RecordingChannelListener listener = new RecordingChannelListener();
        final ChannelManager channelManager = clientTransport
                .createClientChannelPool()
                .build();

        clientTransport.registerChannelListener(listener);

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        final Channel channel = channelManager.requestChannel(addr);

        // when interrupting the channel
        ((ChannelImpl) channel).getSocketChannel().shutdownInput();
        TestUtil.waitUntil(() -> !listener.getInterruptedChannels().isEmpty());

        // then
        TestUtil.waitUntil(() -> channel.isReady());
        assertThat(channel.isReady()).isTrue();
        assertThat(listener.getOpenedChannels()).containsExactly(channel, channel);
    }
    /**
     * This test does not work reliably due to timing issues. The timeout at which a failing
     * connection is detected is platform-specific, so the test cannot make an assumption
     * how long three failing reconnect attempts take.
     */
    @Test
    @Ignore("can be resolved with https://github.com/camunda-tngp/zb-transport/issues/20")
    public void shouldMakeAtMostThreeConsecutiveFailingAttemptsToReopen() throws Exception
    {
        // given
        final RecordingChannelListener listener = new RecordingChannelListener();
        final ChannelManager channelManager = clientTransport
                .createClientChannelPool()
                .build();

        clientTransport.registerChannelListener(listener);

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        final ServerSocketBinding serverSocketBinding = serverTransport.createServerSocketBinding(addr).bind();

        final Channel channel = channelManager.requestChannel(addr);

        // when interrupting the channel permanently
        ((ServerSocketBindingImpl) serverSocketBinding).closeMedia();
        ((ChannelImpl) channel).getSocketChannel().shutdownInput();

        // then
        TestUtil.waitUntil(() -> listener.getInterruptedChannels().size() == 4); // initial failure and three failed reopening attempts
        Thread.sleep(500L); // wait a bit for additional reopen tries
        assertThat(listener.getInterruptedChannels()).containsExactly(channel, channel, channel, channel);
        assertThat(channel.isClosed()).isTrue();
    }

    @Test
    public void shouldNotReopenChannelOnExpectedClose()
    {
        // given
        final RecordingChannelHandler listener = new RecordingChannelHandler();
        final ChannelManager channelManager = clientTransport
                .createClientChannelPool()
                .transportChannelHandler(Protocols.FULL_DUPLEX_SINGLE_MESSAGE, listener)
                .build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        final Channel channel = channelManager.requestChannel(addr);

        // when closing the channel unexpectedly
        ((ChannelImpl) channel).initiateClose();
        TestUtil.waitUntil(() -> !listener.getChannelCloseInvocations().isEmpty());

        // then
        TestUtil.waitUntil(() -> channel.isClosed());
        assertThat(channel.isClosed()).isTrue();
        assertThat(listener.getChannelOpenInvocations()).containsExactly(channel);
    }

    @Test
    public void shouldBeAbleToSendOnReopenedChannel() throws IOException
    {
        // given
        final RecordingChannelHandler clientListener = new RecordingChannelHandler();
        final RecordingChannelHandler serverListener = new RecordingChannelHandler();
        final ChannelManager channelManager = clientTransport
                .createClientChannelPool()
                .transportChannelHandler(Protocols.FULL_DUPLEX_SINGLE_MESSAGE, clientListener)
                .build();

        final DataFramePool dataFramePool = DataFramePool.newBoundedPool(2, clientTransport.getSendBuffer());

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport
            .createServerSocketBinding(addr)
            .transportChannelHandler(serverListener)
            .bind();

        final Channel channel = channelManager.requestChannel(addr);

        // reopening the channel
        ((ChannelImpl) channel).getSocketChannel().shutdownInput();
        TestUtil.waitUntil(() -> clientListener.getChannelOpenInvocations().size() == 2);

        // when
        dataFramePool.openFrame(channel.getStreamId(), 10).commit();

        // then
        TestUtil.waitUntil(() -> !serverListener.getChannelReceiveMessageInvocations().isEmpty());
        assertThat(serverListener.getChannelReceiveMessageInvocations()).hasSize(1);
    }

    @Test
    public void shouldNotReopenChannelIfNotConfigured() throws IOException
    {
        // given
        final RecordingChannelHandler listener = new RecordingChannelHandler();
        final ChannelManager channelManager = clientTransport
                .createClientChannelPool()
                .reopenChannelsOnException(false)
                .transportChannelHandler(Protocols.FULL_DUPLEX_SINGLE_MESSAGE, listener)
                .build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        final Channel channel = channelManager.requestChannel(addr);

        // when closing the channel unexpectedly
        ((ChannelImpl) channel).getSocketChannel().socket().shutdownInput();
        TestUtil.waitUntil(() -> !listener.getChannelCloseInvocations().isEmpty());

        // then
        TestUtil.waitUntil(() -> channel.isClosed());
        assertThat(channel.isClosed()).isTrue();
        assertThat(listener.getChannelOpenInvocations()).containsExactly(channel);
    }

    @Test
    public void shouldEvictOldestUnusedChannelWhenManagerCapacityIsReached()
    {
        // given
        ClockUtil.setCurrentTime(Instant.now());
        final int portRangeStart = 51115;
        final int managerCapacity = 4;
        final ChannelManager channelManager = clientTransport.createClientChannelPool()
            .initialCapacity(managerCapacity)
            .build();

        bindServerSocketsInPortRange(portRangeStart, managerCapacity + 1);
        final Channel[] channels = openStreamsInPortRange(channelManager, portRangeStart, managerCapacity);

        // releasing all channels but the first one
        for (int i = 1; i < channels.length; i++)
        {
            ClockUtil.addTime(Duration.ofSeconds(1));
            channelManager.returnChannel(channels[i]);
        }

        // when opening a new channel such that the capacity is exceeded
        openStreamsInPortRange(channelManager, portRangeStart + managerCapacity, 1);

        // then the oldest unused channel is closed
        TestUtil.waitUntil(() -> channels[1].isClosed());

        assertThat(channels[0].isReady()).isTrue();
        assertThat(channels[1].isClosed()).isTrue();
        assertThat(channels[2].isReady()).isTrue();
        assertThat(channels[3].isReady()).isTrue();
    }

    protected Channel[] openStreamsInPortRange(ChannelManager pool, int firstPort, int range)
    {
        final Channel[] channels = new Channel[range];
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
