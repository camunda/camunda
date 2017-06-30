package io.zeebe.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.impl.ChannelImpl;
import io.zeebe.transport.requestresponse.client.PooledTransportRequest;
import io.zeebe.transport.requestresponse.client.TransportConnection;
import io.zeebe.transport.requestresponse.client.TransportConnectionPool;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import org.junit.*;
import org.junit.rules.ExpectedException;

public class ChannelLifecycleTest
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
            .build();

        serverTransport = Transports.createTransport("server")
            .actorScheduler(actorScheduler)
            .build();
    }

    @After
    public void tearDown()
    {
        clientTransport.close();
        serverTransport.close();
        actorScheduler.close();
    }

    @Test
    public void shouldCloseChannelOnReceiveException() throws IOException
    {
        // given
        final ChannelManager channelManager = clientTransport
                .createClientChannelPool()
                .reopenChannelsOnException(false)
                .build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        final Channel channel = channelManager.requestChannel(addr);
        TestUtil.waitUntil(() -> channel.isReady());
        final SocketChannel socketChannel = ((ChannelImpl) channel).getSocketChannel();

        // when
        socketChannel.shutdownInput();

        // then
        TestUtil.waitUntil(() -> channel.isClosed());
        assertThat(channel.isClosed()).isTrue();
        assertThat(socketChannel.isOpen()).isFalse();
    }

    @Test
    public void shouldCloseChannelOnWriteException() throws IOException
    {
        // given
        final ChannelManager channelManager = clientTransport
                .createClientChannelPool()
                .reopenChannelsOnException(false)
                .build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        final Channel channel = channelManager.requestChannel(addr);
        TestUtil.waitUntil(() -> channel.isReady());
        final SocketChannel socketChannel = ((ChannelImpl) channel).getSocketChannel();

        // when
        socketChannel.shutdownOutput();

        // then
        TestUtil.waitUntil(() -> channel.isClosed());
        assertThat(channel.isClosed()).isTrue();
        assertThat(socketChannel.isOpen()).isFalse();
    }

    @Test
    @Ignore("https://github.com/camunda-tngp/camunda-tngp/issues/286")
    public void shouldNotBeAbleToSendOnClosingChannel() throws IOException
    {
        // given
        final TransportConnectionPool requestPool = TransportConnectionPool.newFixedCapacityPool(clientTransport, 2, 2);
        final ChannelManager channelManager = clientTransport
                .createClientChannelPool()
                .requestResponseProtocol(requestPool)
                .build();

        final SocketAddress addr = new SocketAddress("localhost", 51115);
        serverTransport.createServerSocketBinding(addr).bind();

        final Channel channel = channelManager.requestChannel(addr);
        TestUtil.waitUntil(() -> channel.isReady());
        final SocketChannel socketChannel = ((ChannelImpl) channel).getSocketChannel();

        final TransportConnection transportConnection = requestPool.openConnection();
        final PooledTransportRequest request = transportConnection.openRequest(channel.getStreamId(), 123);

        socketChannel.shutdownOutput();
        request.commit();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Request failed, channel closed");

        // when
        request.awaitResponse(2L, TimeUnit.SECONDS);
    }

}
