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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.test.util.agent.ControllableTaskScheduler;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.time.ClockUtil;

public class ControllableClientTransportTest
{

    public static final DirectBuffer BUF1 = new UnsafeBuffer(new byte[32]);
    public static final SocketAddress SERVER_ADDRESS1 = new SocketAddress("localhost", 51115);

    public static final int SEND_BUFFER_SIZE = 16 * 1024;

    public static final int MESSAGES_REQUIRED_TO_SATURATE_SEND_BUFFER = SEND_BUFFER_SIZE / BUF1.capacity();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Rule
    public ControllableTaskScheduler scheduler = new ControllableTaskScheduler();

    protected ClientTransport clientTransport;

    @Before
    public void setUp()
    {
        final Dispatcher clientSendBuffer = Dispatchers.create("clientSendBuffer")
            .bufferSize(SEND_BUFFER_SIZE)
            .subscriptions("sender")
            .actorScheduler(scheduler)
            .build();

        clientTransport = Transports.newClientTransport()
            .sendBuffer(clientSendBuffer)
            .requestPoolSize(MESSAGES_REQUIRED_TO_SATURATE_SEND_BUFFER + 1)
            .scheduler(scheduler)
            .build();
    }

    @After
    public void tearDown()
    {
        ClockUtil.reset();
        clientTransport.closeAsync();
        scheduler.waitUntilDone();
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

}
