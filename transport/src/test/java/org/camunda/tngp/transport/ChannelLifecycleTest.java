package org.camunda.tngp.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

import org.camunda.tngp.test.util.TestUtil;
import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.camunda.tngp.transport.impl.ChannelImpl;
import org.camunda.tngp.transport.requestresponse.client.PooledTransportRequest;
import org.camunda.tngp.transport.requestresponse.client.TransportConnection;
import org.camunda.tngp.transport.requestresponse.client.TransportConnectionPool;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ChannelLifecycleTest
{

    @Rule
    public ExpectedException exception = ExpectedException.none();

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
        clientTransport.close();
        serverTransport.close();
    }

    @Test
    public void shouldCloseChannelOnReceiveException() throws IOException
    {
        // given
        final ChannelManager channelManager = clientTransport.createClientChannelPool().build();

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
        final ChannelManager channelManager = clientTransport.createClientChannelPool().build();

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
