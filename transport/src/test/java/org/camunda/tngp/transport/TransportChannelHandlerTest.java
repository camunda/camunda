package org.camunda.tngp.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.alignedLength;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.lengthOffset;
import static org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor.typeOffset;
import static org.camunda.tngp.transport.impl.TransportControlFrameDescriptor.TYPE_PROTO_CONTROL_FRAME;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.dispatcher.impl.log.DataFrameDescriptor;
import org.camunda.tngp.test.util.TestUtil;
import org.camunda.tngp.transport.TransportBuilder.ThreadingMode;
import org.camunda.tngp.transport.impl.ChannelImpl;
import org.camunda.tngp.transport.protocol.Protocols;
import org.camunda.tngp.transport.protocol.TransportHeaderDescriptor;
import org.camunda.tngp.transport.singlemessage.DataFramePool;
import org.camunda.tngp.transport.spi.TransportChannelHandler;
import org.camunda.tngp.util.time.ClockUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TransportChannelHandlerTest
{
    protected static final long KEEP_ALIVE_PERIOD = 1000;
    protected static final SocketAddress SERVER_ADDRESS = new SocketAddress("localhost", 51115);

    protected static final DirectBuffer CONTROL_FRAME;

    static
    {
        final UnsafeBuffer writer = new UnsafeBuffer(ByteBuffer.allocate(alignedLength(TransportHeaderDescriptor.HEADER_LENGTH)));

        writer.putInt(lengthOffset(0), 0);
        writer.putShort(typeOffset(0), TYPE_PROTO_CONTROL_FRAME);
        writer.putShort(TransportHeaderDescriptor.protocolIdOffset(DataFrameDescriptor.HEADER_LENGTH), Protocols.FULL_DUPLEX_SINGLE_MESSAGE);

        CONTROL_FRAME = writer;
    }

    protected Transport clientTransport;
    protected Transport serverTransport;

    protected RecordingChannelHandler serverHandler;
    protected RecordingChannelHandler clientHandler;
    protected ChannelManager clientChannelManager;

    @Before
    public void setUp()
    {
        ClockUtil.setCurrentTime(Instant.now());
        clientTransport = Transports.createTransport("client")
            .threadingMode(ThreadingMode.SHARED)
            .channelKeepAlivePeriod(KEEP_ALIVE_PERIOD)
            .build();

        serverTransport = Transports.createTransport("server")
            .threadingMode(ThreadingMode.SHARED)
            .build();

        serverHandler = new RecordingChannelHandler();
        clientHandler = new RecordingChannelHandler();

        clientChannelManager = clientTransport
                .createClientChannelPool()
                .transportChannelHandler(Protocols.FULL_DUPLEX_SINGLE_MESSAGE, clientHandler)
                .build();

        serverTransport
            .createServerSocketBinding(SERVER_ADDRESS)
            .transportChannelHandler(serverHandler)
            .bind();
    }

    @After
    public void tearDown()
    {
        clientTransport.close();
        serverTransport.close();
        ClockUtil.reset();
    }

    @Test
    public void shouldNotifyOnChannelOpened()
    {
        // given
        final Channel channel = clientChannelManager.requestChannel(SERVER_ADDRESS);

        // when
        TestUtil.waitUntil(() -> !clientHandler.getChannelOpenInvocations().isEmpty());

        // then
        assertThat(clientHandler.getChannelOpenInvocations()).containsExactly(channel);
        assertThat(clientHandler.getChannelCloseInvocations()).isEmpty();
        assertThat(clientHandler.getChannelReceiveControlFrameInvocations()).isEmpty();
        assertThat(clientHandler.getChannelReceiveMessageInvocations()).isEmpty();
        assertThat(clientHandler.getChannelSendErrorInvocations()).isEmpty();
        assertThat(clientHandler.getKeepAliveInvocations()).isEmpty();

        TestUtil.waitUntil(() -> !serverHandler.getChannelOpenInvocations().isEmpty());

        assertThat(serverHandler.getChannelOpenInvocations()).hasSize(1);
        assertThat(serverHandler.getChannelCloseInvocations()).isEmpty();
        assertThat(serverHandler.getChannelReceiveControlFrameInvocations()).isEmpty();
        assertThat(serverHandler.getChannelReceiveMessageInvocations()).isEmpty();
        assertThat(serverHandler.getChannelSendErrorInvocations()).isEmpty();
        assertThat(serverHandler.getKeepAliveInvocations()).isEmpty();
    }
    @Test
    public void shouldNotifyOnChannelClosed()
    {
        // given
        final Channel channel = clientChannelManager.requestChannel(SERVER_ADDRESS);
        TestUtil.waitUntil(() -> !serverHandler.getChannelOpenInvocations().isEmpty());

        // when
        clientChannelManager.closeAllChannelsAsync().join();
        TestUtil.waitUntil(() -> channel.isClosed());

        // then
        assertThat(clientHandler.getChannelOpenInvocations()).containsExactly(channel);
        assertThat(clientHandler.getChannelCloseInvocations()).containsExactly(channel);
        assertThat(clientHandler.getChannelReceiveControlFrameInvocations()).isEmpty();
        assertThat(clientHandler.getChannelReceiveMessageInvocations()).isEmpty();
        assertThat(clientHandler.getChannelSendErrorInvocations()).isEmpty();
        assertThat(clientHandler.getKeepAliveInvocations()).isEmpty();

        TestUtil.waitUntil(() -> !serverHandler.getChannelCloseInvocations().isEmpty());

        assertThat(serverHandler.getChannelOpenInvocations()).hasSize(1);
        assertThat(serverHandler.getChannelCloseInvocations()).hasSize(1);
        assertThat(serverHandler.getChannelReceiveControlFrameInvocations()).isEmpty();
        assertThat(serverHandler.getChannelReceiveMessageInvocations()).isEmpty();
        assertThat(serverHandler.getChannelSendErrorInvocations()).isEmpty();
        assertThat(serverHandler.getKeepAliveInvocations()).isEmpty();
    }

    @Test
    public void shouldNotifyOnChannelClosedUnexpectedly() throws IOException
    {
        // given
        final Channel channel = clientChannelManager.requestChannel(SERVER_ADDRESS);
        TestUtil.waitUntil(() -> !serverHandler.getChannelOpenInvocations().isEmpty());

        // when
        ((ChannelImpl) channel).getSocketChannel().shutdownInput();
        TestUtil.waitUntil(() -> !clientHandler.getChannelCloseInvocations().isEmpty());

        // then
        assertThat(clientHandler.getChannelOpenInvocations()).containsExactly(channel);
        assertThat(clientHandler.getChannelCloseInvocations()).containsExactly(channel);
        assertThat(clientHandler.getChannelReceiveControlFrameInvocations()).isEmpty();
        assertThat(clientHandler.getChannelReceiveMessageInvocations()).isEmpty();
        assertThat(clientHandler.getChannelSendErrorInvocations()).isEmpty();
        assertThat(clientHandler.getKeepAliveInvocations()).isEmpty();

        TestUtil.waitUntil(() -> !serverHandler.getChannelCloseInvocations().isEmpty());

        assertThat(serverHandler.getChannelOpenInvocations()).hasSize(1);
        assertThat(serverHandler.getChannelCloseInvocations()).hasSize(1);
        assertThat(serverHandler.getChannelReceiveControlFrameInvocations()).isEmpty();
        assertThat(serverHandler.getChannelReceiveMessageInvocations()).isEmpty();
        assertThat(serverHandler.getChannelSendErrorInvocations()).isEmpty();
        assertThat(serverHandler.getKeepAliveInvocations()).isEmpty();
    }

    @Test
    public void shouldNotifyOnChannelKeepAlive() throws IOException
    {
        // given
        final Channel channel = clientChannelManager.requestChannel(SERVER_ADDRESS);

        // when
        ClockUtil.addTime(Duration.ofMillis(KEEP_ALIVE_PERIOD + 1));
        TestUtil.waitUntil(() -> !serverHandler.getKeepAliveInvocations().isEmpty());

        // then
        assertThat(clientHandler.getChannelOpenInvocations()).containsExactly(channel);
        assertThat(clientHandler.getChannelCloseInvocations()).isEmpty();
        assertThat(clientHandler.getChannelReceiveControlFrameInvocations()).isEmpty();
        assertThat(clientHandler.getChannelReceiveMessageInvocations()).isEmpty();
        assertThat(clientHandler.getChannelSendErrorInvocations()).isEmpty();
        assertThat(clientHandler.getKeepAliveInvocations()).isEmpty();

        assertThat(serverHandler.getChannelOpenInvocations()).hasSize(1);
        assertThat(serverHandler.getChannelCloseInvocations()).isEmpty();
        assertThat(serverHandler.getChannelReceiveControlFrameInvocations()).isEmpty();
        assertThat(serverHandler.getChannelReceiveMessageInvocations()).isEmpty();
        assertThat(serverHandler.getChannelSendErrorInvocations()).isEmpty();
        assertThat(serverHandler.getKeepAliveInvocations()).hasSize(1);
    }

    @Test
    public void shouldNotifyOnControlFrame()
    {
        // given
        final Channel channel = clientChannelManager.requestChannel(SERVER_ADDRESS);

        // when
        channel.scheduleControlFrame(CONTROL_FRAME);
        TestUtil.waitUntil(() -> !serverHandler.getChannelReceiveControlFrameInvocations().isEmpty());

        // then
        assertThat(clientHandler.getChannelOpenInvocations()).containsExactly(channel);
        assertThat(clientHandler.getChannelCloseInvocations()).isEmpty();
        assertThat(clientHandler.getChannelReceiveControlFrameInvocations()).isEmpty();
        assertThat(clientHandler.getChannelReceiveMessageInvocations()).isEmpty();
        assertThat(clientHandler.getChannelSendErrorInvocations()).isEmpty();
        assertThat(clientHandler.getKeepAliveInvocations()).isEmpty();

        assertThat(serverHandler.getChannelOpenInvocations()).hasSize(1);
        assertThat(serverHandler.getChannelCloseInvocations()).isEmpty();
        assertThat(serverHandler.getChannelReceiveControlFrameInvocations()).hasSize(1);
        assertThat(serverHandler.getChannelReceiveMessageInvocations()).isEmpty();
        assertThat(serverHandler.getChannelSendErrorInvocations()).isEmpty();
        assertThat(serverHandler.getKeepAliveInvocations()).isEmpty();
    }

    @Test
    public void shouldNotifyOnMessage()
    {
        // given
        final Channel channel = clientChannelManager.requestChannel(SERVER_ADDRESS);
        final DataFramePool dataframePool = DataFramePool.newBoundedPool(2, clientTransport.getSendBuffer());

        // when
        dataframePool.openFrame(channel.getStreamId(), 4).commit();
        TestUtil.waitUntil(() -> !serverHandler.getChannelReceiveMessageInvocations().isEmpty());

        // then
        assertThat(clientHandler.getChannelOpenInvocations()).containsExactly(channel);
        assertThat(clientHandler.getChannelCloseInvocations()).isEmpty();
        assertThat(clientHandler.getChannelReceiveControlFrameInvocations()).isEmpty();
        assertThat(clientHandler.getChannelReceiveMessageInvocations()).isEmpty();
        assertThat(clientHandler.getChannelSendErrorInvocations()).isEmpty();
        assertThat(clientHandler.getKeepAliveInvocations()).isEmpty();

        assertThat(serverHandler.getChannelOpenInvocations()).hasSize(1);
        assertThat(serverHandler.getChannelCloseInvocations()).isEmpty();
        assertThat(serverHandler.getChannelReceiveControlFrameInvocations()).isEmpty();
        assertThat(serverHandler.getChannelReceiveMessageInvocations()).hasSize(1);
        assertThat(serverHandler.getChannelSendErrorInvocations()).isEmpty();
        assertThat(serverHandler.getKeepAliveInvocations()).isEmpty();
    }

    protected static class RecordingChannelHandler implements TransportChannelHandler
    {

        protected List<Channel> openInvocations = new CopyOnWriteArrayList<>();
        protected List<Channel> closeInvocations = new CopyOnWriteArrayList<>();
        protected List<Channel> sendErrorInvocations = new CopyOnWriteArrayList<>();
        protected List<Channel> receiveMessageInvocations = new CopyOnWriteArrayList<>();
        protected List<Channel> receiveControlFrameInvocations = new CopyOnWriteArrayList<>();
        protected List<Channel> keepAliveInvocations = new CopyOnWriteArrayList<>();

        public List<Channel> getChannelCloseInvocations()
        {
            return closeInvocations;
        }

        public List<Channel> getChannelOpenInvocations()
        {
            return openInvocations;
        }

        public List<Channel> getChannelReceiveControlFrameInvocations()
        {
            return receiveControlFrameInvocations;
        }

        public List<Channel> getChannelReceiveMessageInvocations()
        {
            return receiveMessageInvocations;
        }

        public List<Channel> getChannelSendErrorInvocations()
        {
            return sendErrorInvocations;
        }

        public List<Channel> getKeepAliveInvocations()
        {
            return keepAliveInvocations;
        }

        @Override
        public void onChannelOpened(Channel transportChannel)
        {
            openInvocations.add(transportChannel);
        }

        @Override
        public void onChannelClosed(Channel transportChannel)
        {
            closeInvocations.add(transportChannel);
        }

        @Override
        public void onChannelSendError(Channel transportChannel, DirectBuffer buffer, int offset, int length)
        {
            sendErrorInvocations.add(transportChannel);
        }

        @Override
        public boolean onChannelReceive(Channel transportChannel, DirectBuffer buffer, int offset, int length)
        {
            receiveMessageInvocations.add(transportChannel);
            return true;
        }

        @Override
        public boolean onControlFrame(Channel transportChannel, DirectBuffer buffer, int offset, int length)
        {
            receiveControlFrameInvocations.add(transportChannel);
            return true;
        }

        @Override
        public void onChannelKeepAlive(Channel channel)
        {
            keepAliveInvocations.add(channel);
        }
    }
}
