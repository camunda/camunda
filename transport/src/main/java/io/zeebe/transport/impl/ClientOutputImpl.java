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
package io.zeebe.transport.impl;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Supplier;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.*;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.ZbActorScheduler;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletedActorFuture;
import org.agrona.DirectBuffer;

public class ClientOutputImpl implements ClientOutput
{
    protected final Dispatcher sendBuffer;
    protected final ClientRequestPool requestPool;
    protected final Duration defaultRequestRetryTimeout;
    protected final ZbActorScheduler scheduler;

    public ClientOutputImpl(
            ZbActorScheduler scheduler,
            Dispatcher sendBuffer,
            ClientRequestPool requestPool,
            Duration defaultRequestRetryTimeout)
    {
        this.scheduler = scheduler;
        this.sendBuffer = sendBuffer;
        this.requestPool = requestPool;
        this.defaultRequestRetryTimeout = defaultRequestRetryTimeout;
    }

    @Override
    public boolean sendMessage(TransportMessage transportMessage)
    {
        return transportMessage.trySend(sendBuffer);
    }

    @Override
    public ClientRequest sendRequest(RemoteAddress addr, BufferWriter writer)
    {
        return requestPool.openRequest(addr, writer);
    }

    @Override
    public ActorFuture<ClientRequest> sendRequestWithRetry(RemoteAddress addr, BufferWriter writer, Duration timeout)
    {
        return sendRequestWithRetry(() -> new CompletedActorFuture<>(addr), (b) -> false, writer, timeout);
    }

    @Override
    public ActorFuture<ClientRequest> sendRequestWithRetry(RemoteAddress addr, BufferWriter writer)
    {
        return sendRequestWithRetry(addr, writer, defaultRequestRetryTimeout);
    }

    @Override
    public ActorFuture<ClientRequest> sendRequestWithRetry(Supplier<ActorFuture<RemoteAddress>> remoteAddressSupplier, Function<DirectBuffer, Boolean> responseInspector,
            BufferWriter writer, Duration timeout)
    {
        final ClientRequestRetryController ctrl = new ClientRequestRetryController(remoteAddressSupplier, responseInspector, requestPool, writer, timeout);

        scheduler.submitActor(ctrl);

        return ctrl.getRequest();
    }

}
