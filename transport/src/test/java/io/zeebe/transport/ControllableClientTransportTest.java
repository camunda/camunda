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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import io.zeebe.util.time.ClockUtil;

public class ControllableClientTransportTest
{

    public static final DirectBuffer BUF1 = new UnsafeBuffer(new byte[32]);
    public static final SocketAddress SERVER_ADDRESS1 = new SocketAddress("localhost", 51115);

    public static final int SEND_BUFFER_SIZE = 16 * 1024;

    public static final int MESSAGES_REQUIRED_TO_SATURATE_SEND_BUFFER = SEND_BUFFER_SIZE / BUF1.capacity();

    public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule();
    public AutoCloseableRule closeables = new AutoCloseableRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(closeables);

    protected ClientTransport clientTransport;

    @Before
    public void setUp()
    {
        final Dispatcher clientSendBuffer = Dispatchers.create("clientSendBuffer")
            .bufferSize(SEND_BUFFER_SIZE)
            .actorScheduler(actorSchedulerRule.get())
            .build();

        clientTransport = Transports.newClientTransport()
            .sendBuffer(clientSendBuffer)
            .requestPoolSize(MESSAGES_REQUIRED_TO_SATURATE_SEND_BUFFER + 1)
            .scheduler(actorSchedulerRule.get())
            .build();

        closeables.manage(clientTransport);
    }

    @After
    public void tearDown()
    {
        ClockUtil.reset();
    }

    @Test
    public void shouldNotOpenRequestWhenSendBufferIsSaturated()
    {
        // given
        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);
        final ClientOutput clientOutput = clientTransport.getOutput();

        for (int i = 0; i < MESSAGES_REQUIRED_TO_SATURATE_SEND_BUFFER; i++)
        {
            clientOutput.sendRequest(remoteAddress, new DirectBufferWriter().wrap(BUF1));
        }

        // when
        final ClientRequest request = clientOutput.sendRequest(remoteAddress, new DirectBufferWriter().wrap(BUF1));

        // then
        assertThat(request).isNull();
    }

    @Test
    public void shouldRejectMessageWhenSendBufferIsSaturated()
    {
        // given
        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);
        final ClientOutput clientOutput = clientTransport.getOutput();

        final TransportMessage message = new TransportMessage();
        message.buffer(BUF1);
        message.remoteAddress(remoteAddress);

        for (int i = 0; i < MESSAGES_REQUIRED_TO_SATURATE_SEND_BUFFER; i++)
        {
            clientOutput.sendMessage(message);
        }

        // when
        final boolean success = clientTransport.getOutput().sendMessage(message);

        // then
        assertThat(success).isFalse();
    }

    @Test
    public void shouldCloseTransportWhileWaitingForResponse() throws Exception
    {
        // given
        final AtomicBoolean requestReceived = new AtomicBoolean(false);
        final AtomicBoolean transportClosed = new AtomicBoolean(false);

        buildServerTransport(b -> b.bindAddress(SERVER_ADDRESS1.toInetSocketAddress())
                .build(null, (output, remoteAddress, buffer, offset, length, requestId) ->
                {
                    requestReceived.set(true);
                    return false;
                }));

        final RemoteAddress remote = clientTransport.registerRemoteAddress(SERVER_ADDRESS1);

        final BufferWriter writer = mock(BufferWriter.class);
        when(writer.getLength()).thenReturn(16);

        clientTransport.getOutput().sendRequestWithRetry(remote, writer);

        final Thread closerThread = new Thread(() ->
        {
            clientTransport.close();
            transportClosed.set(true);
        });
        waitUntil(() -> requestReceived.get());

        // when
        closerThread.start();

        // then
        closerThread.join(1000L);
        assertThat(transportClosed).isTrue();
    }


    protected ServerTransport buildServerTransport(Function<ServerTransportBuilder, ServerTransport> builderConsumer)
    {
        final ActorScheduler serverScheduler = ActorScheduler.newDefaultActorScheduler();
        closeables.manage(() ->
        {
            serverScheduler.stop().get();
        });
        serverScheduler.start();

        final Dispatcher serverSendBuffer = Dispatchers.create("serverSendBuffer")
            .bufferSize(SEND_BUFFER_SIZE)
            .actorScheduler(actorSchedulerRule.get())
            .build();
        closeables.manage(serverSendBuffer);

        final ServerTransportBuilder transportBuilder = Transports.newServerTransport()
            .sendBuffer(serverSendBuffer)
            .scheduler(actorSchedulerRule.get());

        final ServerTransport serverTransport = builderConsumer.apply(transportBuilder);
        closeables.manage(serverTransport);

        return serverTransport;
    }
}
