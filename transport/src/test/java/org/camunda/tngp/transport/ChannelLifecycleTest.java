package org.camunda.tngp.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.test.util.TestUtil;
import org.camunda.tngp.transport.impl.ChannelImpl;
import org.camunda.tngp.transport.requestresponse.client.PooledTransportRequest;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.camunda.tngp.util.actor.ActorScheduler;
import org.camunda.tngp.util.actor.ActorSchedulerBuilder;
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
