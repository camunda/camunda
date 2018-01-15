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

import java.util.ArrayDeque;
import java.util.Queue;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.dispatcher.Dispatchers;
import io.zeebe.test.util.AutoCloseableRule;
import io.zeebe.transport.util.EchoRequestResponseHandler;
import io.zeebe.util.actor.ActorScheduler;
import io.zeebe.util.actor.ActorSchedulerBuilder;
import io.zeebe.util.buffer.DirectBufferWriter;

public class RequestResponseTest
{

    protected ServerResponse response = new ServerResponse();
    protected Queue<ClientRequest> pendingRequests = new ArrayDeque<>();
    protected UnsafeBuffer messageBuffer = new UnsafeBuffer(new byte[1024]);
    protected DirectBufferWriter bufferWriter = new DirectBufferWriter();

    private ActorScheduler actorScheduler;

    @Rule
    public AutoCloseableRule closeables = new AutoCloseableRule();

    @Before
    public void setup()
    {
        actorScheduler = ActorSchedulerBuilder.createDefaultScheduler("test");
        closeables.manage(actorScheduler);
    }

    @Test
    public void shouldEchoMessages() throws Exception
    {
        // 1K message
        final SocketAddress addr = new SocketAddress("localhost", 51115);

        final Dispatcher clientSendBuffer = Dispatchers.create("clientSendBuffer")
            .bufferSize(32 * 1024 * 1024)
            .subscriptions(ClientTransportBuilder.SEND_BUFFER_SUBSCRIPTION_NAME)
            .actorScheduler(actorScheduler)
            .build();
        closeables.manage(clientSendBuffer);

        final Dispatcher serverSendBuffer = Dispatchers.create("serverSendBuffer")
            .bufferSize(32 * 1024 * 1024)
            .subscriptions(ServerTransportBuilder.SEND_BUFFER_SUBSCRIPTION_NAME)
            .actorScheduler(actorScheduler)
            .build();
        closeables.manage(serverSendBuffer);

        final ClientTransport clientTransport = Transports.newClientTransport()
            .sendBuffer(clientSendBuffer)
            .requestPoolSize(128)
            .scheduler(actorScheduler)
            .build();
        closeables.manage(clientTransport);

        final ServerTransport serverTransport = Transports.newServerTransport()
            .sendBuffer(serverSendBuffer)
            .bindAddress(addr.toInetSocketAddress())
            .scheduler(actorScheduler)
            .build(null, new EchoRequestResponseHandler());
        closeables.manage(serverTransport);

        final int numRequests = 100_000;
        int numResponsesReceived = 0;
        int numRequestsSent = 0;
        final RemoteAddress remote = clientTransport.registerRemoteAndAwaitChannel(addr);

        while (numResponsesReceived < numRequests)
        {
            while (numRequestsSent < numRequests && sendRequest(clientTransport, remote, numRequestsSent))
            {
                numRequestsSent++;
            }

            final ClientRequest nextPendingRequest = pendingRequests.peek();

            if (nextPendingRequest.isDone())
            {
                final DirectBuffer response = nextPendingRequest.get();
                assertThat(response.getInt(0)).isEqualTo(numResponsesReceived);
                numResponsesReceived++;
                nextPendingRequest.close();
                pendingRequests.remove();
            }
        }
    }

    protected boolean sendRequest(ClientTransport client, RemoteAddress remote, int payload)
    {
        messageBuffer.putInt(0, payload);
        bufferWriter.wrap(messageBuffer, 0, messageBuffer.capacity());
        final ClientRequest request = client.getOutput().sendRequest(remote, bufferWriter);
        if (request != null)
        {
            pendingRequests.add(request);
        }

        return request != null;
    }
}
