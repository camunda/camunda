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
import java.util.Deque;
import java.util.LinkedList;
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.zeebe.transport.*;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.ZbActor;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ClientRequestRetryController extends ZbActor
{
    private static final Duration RESUBMIT_TIMEOUT = Duration.ofMillis(1);

    private final CompletableActorFuture<ClientRequest> successfulRequest = new CompletableActorFuture<>();

    private final ClientRequestPool requestPool;

    private final Supplier<ActorFuture<RemoteAddress>> remoteAddressSupplier;
    private final Duration timeout;

    private final Predicate<DirectBuffer> responseHandler;

    private final UnsafeBuffer writeBuffer = new UnsafeBuffer(0, 0);
    private final DirectBufferWriter requestWriter = new DirectBufferWriter();

    private final Deque<RemoteAddress> remotesTried = new LinkedList<>();

    public ClientRequestRetryController(
            Supplier<ActorFuture<RemoteAddress>> remoteAddressSupplier,
            Predicate<DirectBuffer> responseInspector,
            ClientRequestPool requestPool,
            BufferWriter writer,
            Duration timeout)
    {
        this.remoteAddressSupplier = remoteAddressSupplier;
        this.responseHandler = responseInspector;
        this.requestPool = requestPool;
        this.timeout = timeout;

        final int requestLength = writer.getLength();
        writeBuffer.wrap(new byte[requestLength]); // TODO: we need to pool / recycle this memory
        writer.write(writeBuffer, 0);
        requestWriter.wrap(writeBuffer, 0, requestLength);
    }

    @Override
    protected void onActorStarted()
    {
        actor.run(this::getRemoteAddress);
        actor.runDelayed(timeout, this::onRequestTimedOut);
    }

    private void getRemoteAddress()
    {
        actor.runOnCompletion(remoteAddressSupplier.get(), (address, throwable) ->
        {
            if (address != null)
            {
                if (remotesTried.isEmpty() || !address.equals(remotesTried.peek()))
                {
                    remotesTried.push(address);
                }

                actor.runUntilDone(this::sendRequest);
            }
            else
            {
                actor.runDelayed(RESUBMIT_TIMEOUT, this::getRemoteAddress);
            }
        });
    }

    private ClientRequestImpl currentRequest;

    private void sendRequest()
    {
        currentRequest = requestPool.openRequest(remotesTried.peek(), requestWriter);

        if (currentRequest != null)
        {
            actor.done();

            actor.runOnCompletion(currentRequest, (response, e) ->
            {
                boolean shouldRetry = false;

                if (e != null)
                {
                    shouldRetry = e instanceof NotConnectedException;
                }
                else
                {
                    shouldRetry = responseHandler.test(response);
                }

                if (!shouldRetry)
                {
                    if (!successfulRequest.isDone())
                    {
                        if (e == null)
                        {
                            successfulRequest.complete(currentRequest);
                            currentRequest = null;
                        }
                        else
                        {
                            successfulRequest.completeExceptionally(e);
                        }
                    }
                }
                else
                {
                    currentRequest.close();
                    actor.runDelayed(RESUBMIT_TIMEOUT, this::getRemoteAddress);
                }
            });
        }
        else
        {
            actor.yield(); // retry send
        }
    }

    private void onRequestTimedOut()
    {
        if (successfulRequest.isDone())
        {
            return;
        }

        if (currentRequest != null)
        {
            currentRequest.close();
            currentRequest = null;
        }

        final StringBuilder errBuilder = new StringBuilder("Request timed out after ")
                 .append(timeout)
                 .append(".\nRemotes tried (in reverse order):\n");

        for (RemoteAddress remoteAddress : remotesTried)
        {
            errBuilder.append("\t")
                .append(remoteAddress)
                .append("\n");
        }

        final String errorMessage = errBuilder.toString();
        successfulRequest.completeExceptionally(errorMessage, new RequestTimeoutException(errorMessage));
        actor.close();
    }

    public ActorFuture<ClientRequest> getRequest()
    {
        return successfulRequest;
    }
}
