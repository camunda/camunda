/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.agrona.DirectBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.impl.ControlMessages;
import io.zeebe.transport.util.RecordingChannelListener;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.time.ClockUtil;

public class ClientChannelKeepAliveTest
{
    protected static final DirectBuffer BUF = BufferUtil.wrapBytes(1, 2, 3, 4);

    protected static final long KEEP_ALIVE_PERIOD = 1000;
    protected static final SocketAddress ADDRESS = new SocketAddress("localhost", 51115);
    protected static final SocketAddress ADDRESS2 = new SocketAddress("localhost", 51116);

    protected ServerTransport serverTransport;
    private ActorScheduler actorScheduler;
    private Dispatcher clientSendBuffer;

    protected ControlMessageRecorder serverRecorder;

    @Rule
    public AutoCloseableRule closeables = new AutoCloseableRule();


    @Before
    public void setUp()
    {
        actorScheduler = ActorSchedulerBuilder.createDefaultScheduler("test");
        closeables.manage(actorScheduler);

        clientSendBuffer = Dispatchers.create("clientSendBuffer")
            .bufferSize(32 * 1024)
            .subscriptions("sender")
            .actorScheduler(actorScheduler)
            .build();
        closeables.manage(clientSendBuffer);

        serverRecorder = new ControlMessageRecorder();
        serverTransport = buildServerTransport(ADDRESS, serverRecorder);
    }

    protected ServerTransport buildServerTransport(SocketAddress bindAddress, ControlMessageRecorder recorder)
    {
        final Dispatcher serverSendBuffer = Dispatchers.create("serverSendBuffer")
            .bufferSize(32 * 1024)
            .subscriptions("sender")
            .actorScheduler(actorScheduler)
            .build();
        closeables.manage(serverSendBuffer);

        final ServerTransport serverTransport = Transports.newServerTransport()
            .sendBuffer(serverSendBuffer)
            .bindAddress(bindAddress.toInetSocketAddress())
            .scheduler(actorScheduler)
            .controlMessageListener(recorder)
            .build(null, null);
        closeables.manage(serverTransport);

        return serverTransport;
    }

    protected ClientTransport buildClientTransport(long keepAlivePeriod)
    {
        final ClientTransportBuilder transportBuilder = Transports.newClientTransport()
            .sendBuffer(clientSendBuffer)
            .requestPoolSize(32)
            .scheduler(actorScheduler);

        if (keepAlivePeriod >= 0)
        {
            transportBuilder.keepAlivePeriod(keepAlivePeriod);
        }

        final ClientTransport clientTransport = transportBuilder.build();
        closeables.manage(clientTransport);

        return clientTransport;
    }

    protected void openChannel(ClientTransport transport, SocketAddress target)
    {
        final RecordingChannelListener channelListener = new RecordingChannelListener();
        transport.registerChannelListener(channelListener);

        // make an initial request to open a channel
        final RemoteAddress remoteAddress = transport.registerRemoteAddress(target);
        transport.getOutput().sendRequest(remoteAddress, new DirectBufferWriter().wrap(BUF));

        TestUtil.waitUntil(() -> !channelListener.getOpenedConnections().isEmpty());
    }

    @After
    public void tearDown()
    {
        ClockUtil.reset();
    }

    @Test
    public void shouldSendFirstKeepAlive()
    {
        // given
        final ClientTransport transport = buildClientTransport(KEEP_ALIVE_PERIOD);
        ClockUtil.setCurrentTime(Instant.now());

        openChannel(transport, ADDRESS);

        // when
        ClockUtil.addTime(Duration.ofMillis(KEEP_ALIVE_PERIOD + 1));

        // then
        TestUtil.waitUntil(() -> !serverRecorder.getReceivedFrames().isEmpty());
        assertThat(serverRecorder.getReceivedFrames()).hasSize(1);
        assertThat(serverRecorder.getReceivedFrames().get(0).type).isEqualTo(ControlMessages.KEEP_ALIVE_TYPE);
        assertThat(serverRecorder.getReceivedFrames().get(0).timestamp).isEqualTo(ClockUtil.getCurrentTimeInMillis());
    }

    @Test
    public void shouldSendSubsequentKeepAlives()
    {
        // given
        final ClientTransport transport = buildClientTransport(KEEP_ALIVE_PERIOD);
        ClockUtil.setCurrentTime(Instant.now());

        openChannel(transport, ADDRESS);

        ClockUtil.addTime(Duration.ofMillis(KEEP_ALIVE_PERIOD + 1));
        final long timestamp1 = ClockUtil.getCurrentTimeInMillis();

        TestUtil.waitUntil(() -> !serverRecorder.getReceivedFrames().isEmpty());

        // when
        ClockUtil.addTime(Duration.ofMillis(KEEP_ALIVE_PERIOD + 1));
        final long timestamp2 = ClockUtil.getCurrentTimeInMillis();

        // then
        TestUtil.waitUntil(() -> serverRecorder.getReceivedFrames().size() == 2);
        assertThat(serverRecorder.getReceivedFrames()).hasSize(2);
        assertThat(serverRecorder.getReceivedFrames().get(0).type).isEqualTo(ControlMessages.KEEP_ALIVE_TYPE);
        assertThat(serverRecorder.getReceivedFrames().get(0).timestamp).isEqualTo(timestamp1);
        assertThat(serverRecorder.getReceivedFrames().get(1).type).isEqualTo(ControlMessages.KEEP_ALIVE_TYPE);
        assertThat(serverRecorder.getReceivedFrames().get(1).timestamp).isEqualTo(timestamp2);
    }

    @Test
    public void shouldUseDefaultKeepAlive() throws InterruptedException
    {
        // given
        ClockUtil.setCurrentTime(Instant.now());

        final int expectedDefaultKeepAlive = 5000;
        final ClientTransport transport = buildClientTransport(-1);

        openChannel(transport, ADDRESS);

        // when
        ClockUtil.addTime(Duration.ofMillis(expectedDefaultKeepAlive - 1));
        Thread.sleep(500L);

        assertThat(serverRecorder.getReceivedFrames()).isEmpty();

        // when
        ClockUtil.addTime(Duration.ofMillis(2));

        // then
        TestUtil.waitUntil(() -> !serverRecorder.getReceivedFrames().isEmpty());
        assertThat(serverRecorder.getReceivedFrames()).hasSize(1);
        assertThat(serverRecorder.getReceivedFrames().get(0).type).isEqualTo(ControlMessages.KEEP_ALIVE_TYPE);
        assertThat(serverRecorder.getReceivedFrames().get(0).timestamp).isEqualTo(ClockUtil.getCurrentTimeInMillis());
    }

    @Test
    public void shouldSendKeepAliveForMultipleChannels()
    {
        // given
        ClockUtil.setCurrentTime(Instant.now());
        final ControlMessageRecorder secondServerRecorder = new ControlMessageRecorder();

        buildServerTransport(ADDRESS2, secondServerRecorder);

        final ClientTransport clientTransport = buildClientTransport(KEEP_ALIVE_PERIOD);

        openChannel(clientTransport, ADDRESS);
        openChannel(clientTransport, ADDRESS2);

        // when
        ClockUtil.addTime(Duration.ofMillis(KEEP_ALIVE_PERIOD + 1));

        // then
        TestUtil.waitUntil(() -> !serverRecorder.getReceivedFrames().isEmpty());
        TestUtil.waitUntil(() -> !secondServerRecorder.getReceivedFrames().isEmpty());

        assertThat(serverRecorder.getReceivedFrames()).hasSize(1);
        assertThat(serverRecorder.getReceivedFrames().get(0).timestamp).isEqualTo(ClockUtil.getCurrentTimeInMillis());

        assertThat(secondServerRecorder.getReceivedFrames()).hasSize(1);
        assertThat(secondServerRecorder.getReceivedFrames().get(0).timestamp).isEqualTo(ClockUtil.getCurrentTimeInMillis());

    }

    @Test
    public void shouldNotSendKeepAliveWhenPeriodIsZero() throws Exception
    {
        // given
        ClockUtil.setCurrentTime(Instant.now());
        final ClientTransport clientTransport = buildClientTransport(0);

        openChannel(clientTransport, ADDRESS);

        // when
        ClockUtil.setCurrentTime(Long.MAX_VALUE);
        Thread.sleep(1000L); // can't wait for sender to do nothing, so have to sleep for a bit

        // then
        assertThat(serverRecorder.getReceivedFrames()).isEmpty();
    }

    protected static class ControlMessageRecorder implements ServerControlMessageListener
    {
        protected List<ControlFrame> receivedFrames = new CopyOnWriteArrayList<>();

        @Override
        public void onMessage(ServerOutput output, RemoteAddress remoteAddress, int controlMessageType)
        {
            final ControlFrame frame = new ControlFrame();
            frame.type = controlMessageType;
            frame.timestamp = ClockUtil.getCurrentTimeInMillis();

            receivedFrames.add(frame);
        }

        public List<ControlFrame> getReceivedFrames()
        {
            return receivedFrames;
        }

    }
    protected static class ControlFrame
    {
        int type;
        long timestamp;
    }
}
