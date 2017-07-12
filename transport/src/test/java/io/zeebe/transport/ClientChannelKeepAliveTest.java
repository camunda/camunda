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

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import io.zeebe.util.time.ClockUtil;

@Ignore
public class ClientChannelKeepAliveTest
{
    protected static final long KEEP_ALIVE_PERIOD = 1000;
    protected static final SocketAddress ADDRESS = new SocketAddress("localhost", 51115);

    protected ClientTransport clientTransport;
    protected ServerTransport serverTransport;
    private ActorScheduler actorScheduler;

    @Before
    public void setUp()
    {
        actorScheduler = ActorSchedulerBuilder.createDefaultScheduler("test");

        final Dispatcher clientSendBuffer = Dispatchers.create("clientSendBuffer")
            .bufferSize(32 * 1024 * 1024)
            .subscriptions("sender")
            .actorScheduler(actorScheduler)
            .build();

        final Dispatcher serverSendBuffer = Dispatchers.create("serverSendBuffer")
            .bufferSize(32 * 1024 * 1024)
            .subscriptions("sender")
            .actorScheduler(actorScheduler)
            .build();

        clientTransport = Transports.newClientTransport()
            .sendBuffer(clientSendBuffer)
            // TODO: configure keep-alive period
            .requestPoolSize(128)
            .scheduler(actorScheduler)
            .build();

        serverTransport = Transports.newServerTransport()
            .sendBuffer(serverSendBuffer)
            .bindAddress(ADDRESS.toInetSocketAddress())
            .scheduler(actorScheduler)
            .build(null, null);
    }

    @After
    public void tearDown()
    {
        clientTransport.close();
        serverTransport.close();
        actorScheduler.close();
        ClockUtil.reset();
    }

    // TODO: restore keep-alive
    /*
     * Idea:
     *   * keep-alive as a new message type (single message protocol)
     *   * broker should ignore these messages on all API endpoints
     */

//    @Test
//    public void shouldSendInitialKeepAlive() throws InterruptedException
//    {
//        // given
//        ClockUtil.setCurrentTime(Instant.now());
//
//        final KeepAliveFrameCounter controlFrameHandler = new KeepAliveFrameCounter();
//        serverTransport.createServerSocketBinding(addr)
//            .transportChannelHandler(controlFrameHandler)
//            .bind();
//
//        final ChannelManager channelManager = clientTransport.createClientChannelPool().build();
//
//        final Channel channel = channelManager.requestChannel(addr);
//        TestUtil.waitUntil(() -> channel.isReady());
//
//        // when
//        ClockUtil.addTime(Duration.ofMillis(KEEP_ALIVE_PERIOD + 1));
//
//        // then
//        TestUtil.waitUntil(() -> controlFrameHandler.numReceivedKeepAliveFrames > 0);
//        assertThat(controlFrameHandler.numReceivedKeepAliveFrames).isEqualTo(1);
//    }
//
//    @Test
//    public void shouldSendMoreKeepAliveMessagesAsTimeAdvances()
//    {
//        // given
//        ClockUtil.setCurrentTime(Instant.now());
//
//        final SocketAddress addr = new SocketAddress("localhost", 51115);
//
//        final KeepAliveFrameCounter controlFrameHandler = new KeepAliveFrameCounter();
//        serverTransport.createServerSocketBinding(addr)
//            .transportChannelHandler(controlFrameHandler)
//            .bind();
//
//        final ChannelManager channelManager = clientTransport.createClientChannelPool().build();
//
//        final Channel channel = channelManager.requestChannel(addr);
//        TestUtil.waitUntil(() -> channel.isReady());
//
//        ClockUtil.addTime(Duration.ofMillis(KEEP_ALIVE_PERIOD + 1));
//        TestUtil.waitUntil(() -> controlFrameHandler.numReceivedKeepAliveFrames == 1);
//
//        // when
//        ClockUtil.addTime(Duration.ofMillis(KEEP_ALIVE_PERIOD + 1));
//
//        // then
//        TestUtil.waitUntil(() -> controlFrameHandler.numReceivedKeepAliveFrames > 1);
//        assertThat(controlFrameHandler.numReceivedKeepAliveFrames).isEqualTo(2);
//    }
//
//    protected class KeepAliveFrameCounter implements TransportChannelHandler
//    {
//        protected int numReceivedKeepAliveFrames = 0;
//
//        @Override
//        public void onChannelKeepAlive(Channel channel)
//        {
//            numReceivedKeepAliveFrames++;
//        }
//
//        @Override
//        public void onChannelOpened(Channel transportChannel)
//        {
//        }
//
//        @Override
//        public void onChannelClosed(Channel transportChannel)
//        {
//        }
//
//        @Override
//        public void onChannelSendError(Channel transportChannel, DirectBuffer buffer, int offset, int length)
//        {
//        }
//
//        @Override
//        public boolean onChannelReceive(Channel transportChannel, DirectBuffer buffer, int offset, int length)
//        {
//            return true;
//        }
//
//        @Override
//        public boolean onControlFrame(Channel transportChannel, DirectBuffer buffer, int offset, int length)
//        {
//            return true;
//        }
//
//
//    }
}
