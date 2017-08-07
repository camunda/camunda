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

import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.dispatcher.FragmentHandler;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.transport.impl.TransportChannel.ChannelLifecycleListener;
import io.zeebe.transport.impl.TransportChannelFactory;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.time.ClockUtil;

public class ClientConnectTimeoutTest
{
    protected static final long CONNECT_TIMEOUT = 1234L;

    public static final DirectBuffer BUF1 = BufferUtil.wrapBytes(1, 2, 3, 4);
    public static final SocketAddress SERVER_ADDRESS1 = new SocketAddress("localhost", 51115);
    public static final SocketAddress SERVER_ADDRESS2 = new SocketAddress("localhost", 51116);

    protected UnconnectableChannelFactory channelFactory;

    @Rule
    public AutoCloseableRule closeables = new AutoCloseableRule();

    @Before
    public void setUp()
    {
        this.channelFactory = new UnconnectableChannelFactory();
    }

    protected ClientTransport buildClientTransport(long channelConnectTimeout)
    {
        final ActorScheduler actorScheduler = ActorSchedulerBuilder.createDefaultScheduler("test");
        closeables.manage(actorScheduler);

        final Dispatcher clientSendBuffer = Dispatchers.create("clientSendBuffer")
            .bufferSize(16 * 1024)
            .subscriptions(ClientTransportBuilder.SEND_BUFFER_SUBSCRIPTION_NAME)
            .actorScheduler(actorScheduler)
            .build();
        closeables.manage(clientSendBuffer);

        final ClientTransportBuilder transportBuilder = Transports.newClientTransport()
            .sendBuffer(clientSendBuffer)
            .requestPoolSize(4)
            .scheduler(actorScheduler)
            .channelFactory(channelFactory);

        if (channelConnectTimeout > 0)
        {
            transportBuilder.channelConnectTimeout(channelConnectTimeout);
        }

        final ClientTransport transport = transportBuilder.build();

        closeables.manage(transport);

        return transport;
    }

    @Test
    public void shouldProvideTimeout()
    {
        // given
        final ClientTransport clientTransport = buildClientTransport(CONNECT_TIMEOUT);

        // when
        final long configuredConnectTimeout = clientTransport.getChannelConnectTimeout();

        // then
        assertThat(configuredConnectTimeout).isEqualTo(CONNECT_TIMEOUT);
    }

    @Test
    public void shouldFailRequestAfterChannelConnectTimeout()
    {
        // given
        ClockUtil.setCurrentTime(Instant.now());
        final ClientTransport clientTransport = buildClientTransport(CONNECT_TIMEOUT);

        final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);
        final ClientOutput output = clientTransport.getOutput();

        final ClientRequest request = output.sendRequest(remote, new DirectBufferWriter().wrap(BUF1));

        waitUntil(() -> channelFactory.connectingChannels.get() == 1);
        ClockUtil.addTime(Duration.ofMillis(CONNECT_TIMEOUT + 1));

        // when
        waitUntil(() -> request.isFailed());

        // then
        assertThat(request.isFailed()).isTrue();
        try
        {
            request.get();
            fail("not expected");
        }
        catch (InterruptedException e)
        {
            fail("not expected");
        }
        catch (ExecutionException e)
        {
            assertThat(e).hasMessage("Request failed - Could not open channel");
        }
    }

    @Test
    public void shouldFailRequestsToDifferentRemotes()
    {
        // given
        ClockUtil.setCurrentTime(Instant.now());
        final ClientTransport clientTransport = buildClientTransport(CONNECT_TIMEOUT);

        final RemoteAddress remote1 = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);
        final RemoteAddress remote2 = clientTransport.registerRemoteAddress(SERVER_ADDRESS2);

        final ClientOutput output = clientTransport.getOutput();

        final ClientRequest request1 = output.sendRequest(remote1, new DirectBufferWriter().wrap(BUF1));
        waitUntil(() -> channelFactory.connectingChannels.get() == 1);

        ClockUtil.addTime(Duration.ofMillis(CONNECT_TIMEOUT / 2));

        final ClientRequest request2 = output.sendRequest(remote2, new DirectBufferWriter().wrap(BUF1));
        waitUntil(() -> channelFactory.connectingChannels.get() == 1);

        // when
        ClockUtil.addTime(Duration.ofMillis((CONNECT_TIMEOUT / 2) + 1));
        waitUntil(() -> request1.isFailed());

        // then
        assertThat(request1.isFailed()).isTrue();
        assertThat(request2.isFailed()).isFalse(); // not timed out yet
    }

    @Test
    public void shouldUseDefaultTimeout()
    {
        // given
        final ClientTransport transport = buildClientTransport(-1L);
        final long expectedDefaultTimeout = 500L;

        // when
        final long timeout = transport.getChannelConnectTimeout();

        // then
        assertThat(timeout).isEqualTo(expectedDefaultTimeout);
    }

    protected static class UnconnectableChannelFactory implements TransportChannelFactory
    {

        protected AtomicInteger connectingChannels = new AtomicInteger(0);

        @Override
        public TransportChannel buildClientChannel(ChannelLifecycleListener listener, RemoteAddress remoteAddress,
                int maxMessageSize, FragmentHandler readHandler)
        {
            return new TransportChannel(listener, remoteAddress, maxMessageSize, readHandler)
            {
                @Override
                public void registerSelector(Selector selector, int ops)
                {
                    // never register for OP_CONNECT such that channel never appears to have been connected
                    super.registerSelector(selector, ops & ~SelectionKey.OP_CONNECT);
                }

                @Override
                public boolean beginConnect(CompletableFuture<Void> openFuture)
                {
                    final boolean result = super.beginConnect(openFuture);
                    connectingChannels.incrementAndGet();
                    return result;
                }
            };
        }

        @Override
        public TransportChannel buildServerChannel(ChannelLifecycleListener listener, RemoteAddress remoteAddress,
                int maxMessageSize, FragmentHandler readHandler, SocketChannel media)
        {
            throw new RuntimeException("client only");
        }

    }

}
