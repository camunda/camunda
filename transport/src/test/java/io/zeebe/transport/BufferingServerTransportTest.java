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

import static io.zeebe.test.util.TestUtil.doRepeatedly;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.impl.TransportHeaderDescriptor;
import io.zeebe.transport.util.RecordingMessageHandler;
import io.zeebe.transport.util.TransportTestUtil;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;

public class BufferingServerTransportTest
{
    public static final int BUFFER_SIZE = 16 * 1024;
    public static final SocketAddress SERVER_ADDRESS = new SocketAddress("localhost", 51115);

    @Rule
    public AutoCloseableRule closeables = new AutoCloseableRule();

    protected ClientTransport clientTransport;
    protected BufferingServerTransport serverTransport;

    protected RecordingMessageHandler serverHandler = new RecordingMessageHandler();
    protected RecordingMessageHandler clientHandler = new RecordingMessageHandler();
    private Dispatcher serverReceiveBuffer;

    @Before
    public void setUp()
    {
        final ActorScheduler actorScheduler = ActorSchedulerBuilder.createDefaultScheduler("test");
        closeables.manage(actorScheduler);

        final Dispatcher clientSendBuffer = Dispatchers.create("clientSendBuffer")
            .bufferSize(BUFFER_SIZE)
            .subscriptions(ClientTransportBuilder.SEND_BUFFER_SUBSCRIPTION_NAME)
            .actorScheduler(actorScheduler)
            .build();
        closeables.manage(clientSendBuffer);

        clientTransport = Transports.newClientTransport()
            .sendBuffer(clientSendBuffer)
            .scheduler(actorScheduler)
            .build();
        closeables.manage(clientTransport);

        final Dispatcher serverSendBuffer = Dispatchers.create("serverSendBuffer")
            .bufferSize(BUFFER_SIZE)
            .subscriptions(ServerTransportBuilder.SEND_BUFFER_SUBSCRIPTION_NAME)
            .actorScheduler(actorScheduler)
            .build();
        closeables.manage(serverSendBuffer);

        serverReceiveBuffer = Dispatchers.create("serverReceiveBuffer")
            .bufferSize(BUFFER_SIZE)
            .actorScheduler(actorScheduler)
            .build();
        closeables.manage(serverReceiveBuffer);

        serverTransport = Transports.newServerTransport()
            .sendBuffer(serverSendBuffer)
            .scheduler(actorScheduler)
            .bindAddress(SERVER_ADDRESS.toInetSocketAddress())
            .buildBuffering(serverReceiveBuffer);
        closeables.manage(serverTransport);
    }

    @Test
    public void shouldPostponeMessagesOnReceiveBufferBackpressure() throws InterruptedException
    {
        // given
        final int maximumMessageLength = serverReceiveBuffer.getMaxFrameLength()
                - TransportHeaderDescriptor.HEADER_LENGTH
                - 1; // https://github.com/zeebe-io/zb-dispatcher/issues/21

        final DirectBuffer largeBuf = new UnsafeBuffer(new byte[maximumMessageLength]);

        final int messagesToExhaustReceiveBuffer = (BUFFER_SIZE / largeBuf.capacity()) + 1;

        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(SERVER_ADDRESS);

        final ServerInputSubscription serverSubscription = serverTransport.openSubscription("foo", serverHandler, null).join();

        // exhaust server's receive buffer
        final TransportMessage message = new TransportMessage().buffer(largeBuf).remoteAddress(remoteAddress);
        for (int i = 0; i < messagesToExhaustReceiveBuffer; i++)
        {
            doRepeatedly(() -> clientTransport.getOutput().sendMessage(message)).until(s -> s);
        }

        TransportTestUtil.waitUntilExhausted(serverReceiveBuffer);
        Thread.sleep(200L); // give transport some time to try to push things on top

        // when
        final AtomicInteger receivedMessages = new AtomicInteger(0);
        doRepeatedly(() ->
        {
            final int polledMessages = serverSubscription.poll();
            return receivedMessages.addAndGet(polledMessages);
        }).until(m -> m == messagesToExhaustReceiveBuffer);

        // then
        assertThat(receivedMessages.get()).isEqualTo(messagesToExhaustReceiveBuffer);
    }

}
