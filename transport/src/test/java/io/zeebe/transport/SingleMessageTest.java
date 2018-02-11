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

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class SingleMessageTest
{
    public ActorSchedulerRule actorSchedulerRule = new ActorSchedulerRule(3);
    public AutoCloseableRule closeables = new AutoCloseableRule();

    @Rule
    public RuleChain ruleChain = RuleChain.outerRule(actorSchedulerRule).around(closeables);

    protected final TransportMessage clientMessage = new TransportMessage();
    protected final TransportMessage serverMessage = new TransportMessage();
    protected UnsafeBuffer messageBuffer = new UnsafeBuffer(new byte[1024]);

    @Test
    public void shouldEchoMessages() throws Exception
    {
        final SocketAddress addr = new SocketAddress("localhost", 51115);
        final int numRequests = 10_000_000;

        final CountingListener responseCounter = new CountingListener();

        final Dispatcher clientSendBuffer = Dispatchers.create("clientSendBuffer")
                .bufferSize(32 * 1024 * 1024)
                .actorScheduler(actorSchedulerRule.get())
                .build();
        closeables.manage(clientSendBuffer);

        final Dispatcher serverSendBuffer = Dispatchers.create("serverSendBuffer")
            .bufferSize(32 * 1024 * 1024)
            .actorScheduler(actorSchedulerRule.get())
            .build();
        closeables.manage(serverSendBuffer);

        final ClientTransport clientTransport = Transports.newClientTransport()
            .sendBuffer(clientSendBuffer)
            .requestPoolSize(128)
            .scheduler(actorSchedulerRule.get())
            .inputListener(responseCounter)
            .build();
        closeables.manage(clientTransport);

        final ServerTransport serverTransport = Transports.newServerTransport()
            .sendBuffer(serverSendBuffer)
            .bindAddress(addr.toInetSocketAddress())
            .scheduler(actorSchedulerRule.get())
            .build((output, remote, buf, offset, length) ->
            {
                serverMessage
                    .reset()
                    .buffer(buf, offset, length)
                    .remoteStreamId(remote.getStreamId());
                return output.sendMessage(serverMessage);
            }, null);
        closeables.manage(serverTransport);

        final RemoteAddress remoteAddress = clientTransport.registerRemoteAndAwaitChannel(addr);

        for (int i = 0; i < numRequests; i++)
        {
            messageBuffer.putInt(0, i);
            clientMessage.reset()
                .buffer(messageBuffer)
                .remoteAddress(remoteAddress);

            while (!clientTransport.getOutput().sendMessage(clientMessage))
            {
                // spin
            }
        }

        while (responseCounter.numMessagesReceived < numRequests)
        {
        }

        assertThat(responseCounter.numMessagesReceived).isEqualTo(numRequests);

        actorSchedulerRule.get().dumpMetrics(System.out);
    }

    protected static class CountingListener implements ClientInputListener
    {

        protected volatile int numMessagesReceived = 0;

        @Override
        public void onResponse(int streamId, long requestId, DirectBuffer buffer, int offset, int length)
        {
        }

        @Override
        public void onMessage(int streamId, DirectBuffer buffer, int offset, int length)
        {
            numMessagesReceived++;
        }
    }

}
