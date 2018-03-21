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
import java.util.function.Predicate;
import java.util.function.Supplier;

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.*;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.buffer.DirectBufferWriter;
import io.zeebe.util.sched.*;
import io.zeebe.util.sched.clock.ActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.*;
import org.agrona.concurrent.ManyToManyConcurrentArrayQueue;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class ClientRequestPool implements AutoCloseable
{
    private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

    private final ActorScheduler scheduler;
    private final int capacity;
    private final ManyToManyConcurrentArrayQueue<ClientRequestController> availableRequests;
    private ClientRequestController[] requests;
    private volatile boolean isClosed = false;

    public ClientRequestPool(ActorScheduler scheduler, int requestedCapacity, Dispatcher sendBuffer)
    {
        capacity = BitUtil.findNextPositivePowerOfTwo(requestedCapacity);

        availableRequests = new ManyToManyConcurrentArrayQueue<>(capacity);
        requests = new ClientRequestController[capacity];

        for (int i = 0; i < capacity; i++)
        {
            final ClientRequestController request = new ClientRequestController(new PooledRequestIdGenerator(i, capacity), sendBuffer, this::onRequestClosed);
            requests[i] = request;
            availableRequests.add(request);
            scheduler.submitActor(request);
        }

        this.scheduler = scheduler;
    }

    public ActorFuture<ClientResponse> openRequest(Supplier<ActorFuture<RemoteAddress>> remoteAddressSupplier,
            Predicate<DirectBuffer> responseInspector,
            BufferWriter writer,
            Duration timeout)
    {
        final CompletableActorFuture<ClientResponse> responseFuture = new CompletableActorFuture<>();

        // attempt to use pooled request
        final ClientRequestController request = availableRequests.poll();

        if (request != null)
        {
            request.init(responseFuture, remoteAddressSupplier, responseInspector, writer, timeout);
        }
        else
        {
            LOG.debug("No pooled request available.");
            // submit actor re-attempting to send the request
            scheduler.submitActor(new DeferredRequestAllocator(responseFuture, remoteAddressSupplier, responseInspector, writer, timeout));
        }

        return responseFuture;
    }

    public ClientRequestController getOpenRequestById(long id)
    {
        ClientRequestController result = null;

        final int offset = (int) (id & (capacity - 1));
        final ClientRequestController request = requests[offset];

        if (request.getCurrentRequestId() == id)
        {
            result = request;
        }

        return result;
    }

    public void failPendingRequestsToRemote(RemoteAddress remote, String reason)
    {
        for (int i = 0; i < requests.length; i++)
        {
            requests[i].failPendingRequestToRemote(remote, reason);
        }
    }

    @Override
    public void close()
    {
        this.isClosed = true;

        for (int i = 0; i < requests.length; i++)
        {
            // TODO: wait until closed
            requests[i].closeActor();
        }

        this.availableRequests.clear();
    }

    public void onRequestClosed(ClientRequestController request)
    {
        if (!isClosed)
        {
            availableRequests.add(request);
        }
    }

    public interface RequestIdGenerator
    {
        long getNextRequestId();
    }

    private static class PooledRequestIdGenerator implements RequestIdGenerator
    {
        private final int poolCapacity;
        private long lastId;

        PooledRequestIdGenerator(int offset, int poolCapacity)
        {
            this.poolCapacity = poolCapacity;
            this.lastId = offset;
        }

        @Override
        public long getNextRequestId()
        {
            lastId += poolCapacity;
            return lastId;
        }
    }

    /**
     * Used when no pooled request is immediately available when the user submits a request.
     * Attempts re-attempts to allocate the request until the request timeout is reached.
     */
    class DeferredRequestAllocator extends Actor
    {
        private long submitMs;

        private final CompletableActorFuture<ClientResponse> responseFuture;
        private final Supplier<ActorFuture<RemoteAddress>> remoteAddressSupplier;
        private final Predicate<DirectBuffer> responseInspector;
        private final Duration timeout;
        private final BufferWriter requestWriter;

        private boolean isTimeout = false;

        DeferredRequestAllocator(
                CompletableActorFuture<ClientResponse> responseFuture,
                Supplier<ActorFuture<RemoteAddress>> remoteAddressSupplier,
                Predicate<DirectBuffer> responseInspector,
                BufferWriter writer,
                Duration timeout)
        {
            this.responseFuture = responseFuture;
            this.remoteAddressSupplier = remoteAddressSupplier;
            this.responseInspector = responseInspector;
            this.timeout = timeout;

            // make additional copy of request buffer
            final UnsafeBuffer resquetBuffer = new UnsafeBuffer(new byte[writer.getLength()]);
            writer.write(resquetBuffer, 0);
            this.requestWriter = new DirectBufferWriter().wrap(resquetBuffer);
        }

        @Override
        protected void onActorStarted()
        {
            // record time when actor is submitted
            submitMs = ActorClock.currentTimeMillis();
            actor.runDelayed(timeout, this::onTimeout);

            attemptInit();
        }

        protected void attemptInit()
        {
            if (!isTimeout)
            {
                final ClientRequestController request = availableRequests.poll();

                if (request != null)
                {
                    // subtract time spent in this actor from request timeout
                    final Duration remainingTimeout = timeout.minusMillis(ActorClock.currentTimeMillis() - submitMs);
                    request.init(responseFuture, remoteAddressSupplier, responseInspector, requestWriter, remainingTimeout);
                    actor.close();
                }
                else
                {
                    // re-attempt submit (do not use run until done so that the timeout can fire)
                    actor.submit(this::attemptInit);
                    actor.yield();
                }
            }
        }

        private void onTimeout()
        {
            isTimeout = true;
            responseFuture.completeExceptionally(new RequestTimeoutException("Request timed out due to backpressure"));
            actor.close();
        }
    }
}
