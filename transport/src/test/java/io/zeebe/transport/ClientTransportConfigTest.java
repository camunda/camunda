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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.impl.RequestManager;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.buffer.DirectBufferWriter;

public class ClientTransportConfigTest
{
    public static final int BUFFER_SIZE = 16 * 1024;

    @Rule
    public AutoCloseableRule closeables = new AutoCloseableRule();

    private Dispatcher sendBuffer;
    private ActorScheduler actorScheduler;

    @Before
    public void setUp()
    {
        actorScheduler = spy(ActorSchedulerBuilder.createDefaultScheduler("test"));
        closeables.manage(actorScheduler);

        sendBuffer = Dispatchers.create("clientSendBuffer")
            .bufferSize(BUFFER_SIZE)
            .subscriptions(ClientTransportBuilder.SEND_BUFFER_SUBSCRIPTION_NAME)
            .actorScheduler(actorScheduler)
            .build();
        closeables.manage(sendBuffer);
    }

    @Test
    public void shouldNotMakeManagedRequestsWhenNotEnabled()
    {
        // given
        final ClientTransport clientTransport = Transports.newClientTransport()
                .sendBuffer(sendBuffer)
                .scheduler(actorScheduler)
                .build();

        final ClientOutput output = clientTransport.getOutput();

        final DirectBufferWriter writer = new DirectBufferWriter();
        writer.wrap(BufferUtil.wrapString("foo"));

        final RemoteAddress remoteAddress = clientTransport.registerRemoteAddress(new SocketAddress("localhost", 51015));

        // when/then
        assertThatThrownBy(() -> output.sendRequestWithRetry(remoteAddress, writer))
            .isInstanceOf(UnsupportedOperationException.class);

        verify(actorScheduler, never()).schedule(Mockito.any(RequestManager.class));
    }
}
