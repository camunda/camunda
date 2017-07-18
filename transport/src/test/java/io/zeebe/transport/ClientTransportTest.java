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
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import io.zeebe.test.util.TestUtil;
import io.zeebe.test.util.io.FailingBufferWriter;
import io.zeebe.test.util.io.FailingBufferWriter.FailingBufferWriterException;
import io.zeebe.transport.impl.RemoteAddressList;
import io.zeebe.transport.impl.TransportChannel;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.DirectBufferWriter;

public class ClientTransportTest
{

    public static final DirectBuffer BUF1 = BufferUtil.wrapBytes(1, 2, 3, 4);
    public static final SocketAddress SERVER_ADDRESS1 = new SocketAddress("localhost", 51115);
    public static final SocketAddress SERVER_ADDRESS2 = new SocketAddress("localhost", 51116);

    public static final int REQUEST_POOL_SIZE = 4;
    public static final int SEND_BUFFER_SIZE = 16 * 1024;

    @Rule
    public AutoCloseableRule closeables = new AutoCloseableRule();

    protected ClientTransport clientTransport;
    protected DummyServerTransport serverTransport;

    private ActorScheduler actorScheduler;

    @Before
    public void setUp()
    {
        actorScheduler = ActorSchedulerBuilder.createDefaultScheduler("test");
        closeables.manage(actorScheduler);

        final Dispatcher clientSendBuffer = Dispatchers.create("clientSendBuffer")
            .bufferSize(SEND_BUFFER_SIZE)
            .subscriptions("sender")
            .actorScheduler(actorScheduler)
            .build();
        closeables.manage(clientSendBuffer);

        clientTransport = Transports.newClientTransport()
                .sendBuffer(clientSendBuffer)
                .requestPoolSize(REQUEST_POOL_SIZE)
                .scheduler(actorScheduler)
                .build();
        closeables.manage(clientTransport);

        serverTransport = new DummyServerTransport();
        closeables.manage(serverTransport);
    }

    @Test
    public void shouldUseSameChannelForConsecutiveRequestsToSameRemote()
    {
        // given
        serverTransport.listenOn(SERVER_ADDRESS1);

        final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);
        final ClientOutput output = clientTransport.getOutput();

        output.sendRequest(remote, new DirectBufferWriter().wrap(BUF1));
        output.sendRequest(remote, new DirectBufferWriter().wrap(BUF1));

        final AtomicInteger messageCounter = serverTransport.acceptNextConnection(SERVER_ADDRESS1);

        // when
        TestUtil.doRepeatedly(() -> serverTransport.receive(SERVER_ADDRESS1))
            .until((r) -> messageCounter.get() == 2);

        // then
        assertThat(serverTransport.getClientChannels(SERVER_ADDRESS1)).hasSize(1);
    }

    @Test
    public void shouldUseDifferentChannelsForDifferentRemotes()
    {
        // given
        serverTransport.listenOn(SERVER_ADDRESS1);
        serverTransport.listenOn(SERVER_ADDRESS2);
        final ClientOutput output = clientTransport.getOutput();

        final RemoteAddress remote1 = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);
        final RemoteAddress remote2 = clientTransport.registerRemoteAddress(SERVER_ADDRESS2);

        output.sendRequest(remote1, new DirectBufferWriter().wrap(BUF1));
        output.sendRequest(remote2, new DirectBufferWriter().wrap(BUF1));

        // when
        final AtomicInteger messageCounter1 = serverTransport.acceptNextConnection(SERVER_ADDRESS1);
        final AtomicInteger messageCounter2 = serverTransport.acceptNextConnection(SERVER_ADDRESS2);

        // then
        TestUtil.doRepeatedly(() -> serverTransport.receive(SERVER_ADDRESS1))
            .until((r) -> messageCounter1.get() == 1);
        TestUtil.doRepeatedly(() -> serverTransport.receive(SERVER_ADDRESS2))
            .until((r) -> messageCounter2.get() == 1);

        assertThat(serverTransport.getClientChannels(SERVER_ADDRESS1)).hasSize(1);
        assertThat(serverTransport.getClientChannels(SERVER_ADDRESS2)).hasSize(1);
    }

    @Test
    public void shouldOpenNewChannelOnceChannelIsClosed()
    {
        // given
        serverTransport.listenOn(SERVER_ADDRESS1);

        final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);
        final ClientOutput output = clientTransport.getOutput();

        final ClientRequest request = output.sendRequest(remote, new DirectBufferWriter().wrap(BUF1));
        serverTransport.acceptNextConnection(SERVER_ADDRESS1);

        serverTransport.getClientChannels(SERVER_ADDRESS1).get(0).close();
        TestUtil.waitUntil(() -> request.isFailed());

        // when
        output.sendRequest(remote, new DirectBufferWriter().wrap(BUF1));

        // then
        final AtomicInteger messageCounter = serverTransport.acceptNextConnection(SERVER_ADDRESS1);

        TestUtil.doRepeatedly(() -> serverTransport.receive(SERVER_ADDRESS1))
            .until((r) -> messageCounter.get() == 1);

        // then
        assertThat(serverTransport.getClientChannels(SERVER_ADDRESS1)).hasSize(2);
    }

    @Test
    public void shouldCloseChannelsWhenTransportCloses()
    {
        // given
        serverTransport.listenOn(SERVER_ADDRESS1);

        final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);
        final ClientOutput output = clientTransport.getOutput();

        output.sendRequest(remote, new DirectBufferWriter().wrap(BUF1));
        final AtomicInteger messageCounter = serverTransport.acceptNextConnection(SERVER_ADDRESS1);
        TestUtil.doRepeatedly(() -> serverTransport.receive(SERVER_ADDRESS1)).until(i -> messageCounter.get() == 1);

        // when
        clientTransport.close();
        serverTransport.receive(SERVER_ADDRESS1); // receive once more to make server recognize that channel has closed

        // then
        final TransportChannel channel = serverTransport.getClientChannels(SERVER_ADDRESS1).get(0);
        TestUtil.waitUntil(() -> !channel.getNioChannel().isOpen());

        assertThat(serverTransport.getClientChannels(SERVER_ADDRESS1)).hasSize(1);
        assertThat(channel.getNioChannel().isOpen()).isFalse();
    }

    @Test
    public void shouldFailRequestWhenChannelDoesNotConnect()
    {
        // given
        final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);
        final ClientOutput output = clientTransport.getOutput();

        // when
        final ClientRequest request = output.sendRequest(remote, new DirectBufferWriter().wrap(BUF1));

        // then
        TestUtil.waitUntil(() -> request.isFailed());

        assertThat(request.isFailed()).isTrue();

        try
        {
            request.get();
            fail("Should not resolve");
        }
        catch (Exception e)
        {
            assertThat(e).isInstanceOf(ExecutionException.class);
        }
    }

    @Test
    public void shouldNotOpenRequestWhenClienRequestPoolCapacityIsExceeded()
    {
        // given
        final ClientOutput clientOutput = clientTransport.getOutput();
        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

        for (int i = 0; i < REQUEST_POOL_SIZE; i++)
        {
            clientOutput.sendRequest(remoteAddress, new DirectBufferWriter().wrap(BUF1));
        }

        // when
        final ClientRequest request = clientOutput.sendRequest(remoteAddress, new DirectBufferWriter().wrap(BUF1));

        // then
        assertThat(request).isNull();

    }

    @Test
    public void shouldReuseRequestOnceClosed()
    {
        // given
        final ClientOutput clientOutput = clientTransport.getOutput();
        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

        for (int i = 0; i < REQUEST_POOL_SIZE - 1; i++)
        {
            clientOutput.sendRequest(remoteAddress, new DirectBufferWriter().wrap(BUF1));
        }

        final ClientRequest request = clientOutput.sendRequest(remoteAddress, new DirectBufferWriter().wrap(BUF1));
        TestUtil.waitUntil(() -> request.isFailed());
        request.close();

        serverTransport.listenOn(SERVER_ADDRESS1); // don't let request fail next time

        // when
        final ClientRequest newRequest = clientOutput.sendRequest(remoteAddress, new DirectBufferWriter().wrap(BUF1));

        // then
        assertThat(newRequest).isNotNull();
        assertThat(newRequest).isSameAs(request); // testing object identity may be too strict from an API perspective but is good to identify technical issues

        // and the request state should be reset
        assertThat(newRequest.isDone()).isFalse();
        assertThat(newRequest.isFailed()).isFalse();
    }

    @Test
    public void shouldReturnRequestToPoolWhenBufferWriterFails()
    {
        // given
        final FailingBufferWriter failingWriter = new FailingBufferWriter();
        final DirectBufferWriter successfulWriter = new DirectBufferWriter().wrap(BUF1);

        final ClientOutput clientOutput = clientTransport.getOutput();
        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

        for (int i = 0; i < REQUEST_POOL_SIZE; i++)
        {
            try
            {
                clientOutput.sendRequest(remoteAddress, failingWriter);
            }
            catch (FailingBufferWriterException e)
            {
                // expected
            }
        }

        // when
        final ClientRequest request = clientOutput.sendRequest(remoteAddress, successfulWriter);

        // then
        assertThat(request).isNotNull();
    }

    protected static class DummyServerTransport implements AutoCloseable
    {
        protected Map<SocketAddress, ServerSocketChannel> serverChannels = new HashMap<>();
        protected Map<SocketAddress, List<TransportChannel>> clientChannels = new HashMap<>();
        protected RemoteAddressList remoteList = new RemoteAddressList();

        public void listenOn(SocketAddress localAddress)
        {
            ServerSocketChannel socketChannel = null;
            try
            {
                socketChannel = ServerSocketChannel.open();
                socketChannel.bind(localAddress.toInetSocketAddress());
                socketChannel.configureBlocking(true);
                serverChannels.put(localAddress, socketChannel);
            }
            catch (IOException e1)
            {
                if (socketChannel != null)
                {
                    try
                    {
                        socketChannel.close();
                    }
                    catch (IOException e2)
                    {
                        throw new RuntimeException(e2);
                    }
                }
                throw new RuntimeException(e1);
            }
        }

        public AtomicInteger acceptNextConnection(SocketAddress localAddress)
        {
            final AtomicInteger messageCounter = new AtomicInteger(0);
            acceptNextConnection(localAddress, messageCounter);
            return messageCounter;
        }

        public List<TransportChannel> getClientChannels(SocketAddress localAddress)
        {
            return clientChannels.get(localAddress);
        }

        public void acceptNextConnection(SocketAddress localAddress, AtomicInteger messageCounter)
        {
            try
            {
                final SocketChannel clientChannel = serverChannels.get(localAddress).accept();
                clientChannel.configureBlocking(false);
                final RemoteAddress remoteAddress = remoteList.register(new SocketAddress((InetSocketAddress) clientChannel.getRemoteAddress()));

                final TransportChannel c = new TransportChannel(
                    null,
                    remoteAddress,
                    128,
                    (b, o, l, streamId, failed) ->
                    {
                        messageCounter.incrementAndGet();
                        return FragmentHandler.CONSUME_FRAGMENT_RESULT;
                    },
                    null,
                    clientChannel);

                clientChannels.computeIfAbsent(localAddress, a -> new ArrayList<>());
                clientChannels.get(localAddress).add(c);
            }
            catch (IOException e)
            {
                throw new RuntimeException(e);
            }
        }

        public int receive(SocketAddress localAddress)
        {
            return clientChannels.get(localAddress).stream().mapToInt(c -> c.receive()).sum();
        }

        public void close()
        {
            clientChannels.forEach((a, channels) -> channels.forEach(c -> c.close()));

            for (ServerSocketChannel channel : serverChannels.values())
            {
                try
                {
                    channel.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }
}
