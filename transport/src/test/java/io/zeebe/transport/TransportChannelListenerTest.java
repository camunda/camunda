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

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.test.util.TestUtil;
import io.zeebe.transport.util.RecordingChannelListener;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import io.zeebe.util.buffer.DirectBufferWriter;

public class TransportChannelListenerTest
{

    private static final SocketAddress ADDRESS = new SocketAddress("localhost", 51115);
    protected static final DirectBuffer EMPTY_BUFFER = new UnsafeBuffer(0, 0);

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
    }

    @Test
    public void shouldInvokeRegisteredListenerOnChannelClose() throws InterruptedException
    {
        // given
        final RecordingChannelListener clientListener = new RecordingChannelListener();
        clientTransport.registerChannelListener(clientListener);

        final RecordingChannelListener serverListener = new RecordingChannelListener();
        serverTransport.registerChannelListener(serverListener);

        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(ADDRESS);

        // opens a channel asynchronously
        clientTransport.getOutput().sendRequest(remoteAddress, new DirectBufferWriter().wrap(EMPTY_BUFFER));

        TestUtil.waitUntil(() -> !clientListener.getOpenedConnections().isEmpty());

        // when
        clientTransport.closeAllChannels().join();

        // then
        TestUtil.waitUntil(() -> !clientListener.getClosedConnections().isEmpty());
        assertThat(clientListener.getClosedConnections()).hasSize(1);
        assertThat(clientListener.getClosedConnections().get(0)).isSameAs(remoteAddress);

        // TODO: cannot reliably wait for channel being closed on server-side, since
        //   channel close notification happens asynchronously after client channel close
//        assertThat(serverListener.closedChannels).hasSize(1);

    }

    @Test
    public void shouldInvokeRegisteredListenerOnChannelOpened() throws InterruptedException
    {
        // given
        final RecordingChannelListener clientListener = new RecordingChannelListener();
        clientTransport.registerChannelListener(clientListener);

        final RecordingChannelListener serverListener = new RecordingChannelListener();
        serverTransport.registerChannelListener(serverListener);

        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(ADDRESS);

        // when
        clientTransport.getOutput().sendRequest(remoteAddress, new DirectBufferWriter().wrap(EMPTY_BUFFER));

        // then
        TestUtil.waitUntil(() -> !clientListener.getOpenedConnections().isEmpty());
        TestUtil.waitUntil(() -> !serverListener.getOpenedConnections().isEmpty());

        assertThat(clientListener.getOpenedConnections()).containsExactly(remoteAddress);
        assertThat(serverListener.getOpenedConnections()).hasSize(1);
    }

    @Test
    public void shouldDeregisterListener()
    {
        // given
        final RecordingChannelListener clientListener = new RecordingChannelListener();
        clientTransport.registerChannelListener(clientListener);

        final RecordingChannelListener serverListener = new RecordingChannelListener();
        serverTransport.registerChannelListener(serverListener);

        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(ADDRESS);

        clientTransport.getOutput().sendRequest(remoteAddress, new DirectBufferWriter().wrap(EMPTY_BUFFER));
        TestUtil.waitUntil(() -> !clientListener.getOpenedConnections().isEmpty());

        clientTransport.removeChannelListener(clientListener);
        serverTransport.removeChannelListener(serverListener);

        // when
        clientTransport.closeAllChannels().join();

        // then

        assertThat(clientListener.getClosedConnections()).hasSize(0);
        assertThat(serverListener.getClosedConnections()).hasSize(0);
    }

}
