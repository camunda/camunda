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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.*;
import io.zeebe.transport.impl.ClientRequestPool.RequestIdGenerator;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.Actor;
import io.zeebe.util.sched.ScheduledTimer;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.*;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class ClientRequestController extends Actor
{
    private static final Logger LOG = Loggers.TRANSPORT_LOGGER;

    private static final Duration RESUBMIT_TIMEOUT = Duration.ofMillis(1);

    private final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    private final RequestResponseHeaderDescriptor requestResponseHeader = new RequestResponseHeaderDescriptor();

    private final RequestIdGenerator requestIdGenerator;
    private final Dispatcher sendBuffer;
    private final Consumer<ClientRequestController> onRequestClosed;
    private final ClaimedFragment sendBufferClaim = new ClaimedFragment();

    private final ExpandableArrayBuffer requestBuffer = new ExpandableArrayBuffer(128);
    private int requestLength;

    private final MutableDirectBuffer responseBuffer = new ExpandableArrayBuffer();
    private final UnsafeBuffer responseBufferView = new UnsafeBuffer(0, 0);

    private final Deque<RemoteAddress> remotesTried = new LinkedList<>();

    private Supplier<ActorFuture<RemoteAddress>> remoteAddressSupplier;
    private Predicate<DirectBuffer> responseHandler;
    private Duration timeout;
    private ScheduledTimer scheduledTimeout;

    private volatile long requestId;
    private volatile CompletableActorFuture<ClientResponse> responseFuture;

    public ClientRequestController(RequestIdGenerator requestIdGenerator,
            Dispatcher sendBuffer,
            Consumer<ClientRequestController> onRequestClosed)
    {
        this.requestIdGenerator = requestIdGenerator;
        this.sendBuffer = sendBuffer;
        this.onRequestClosed = onRequestClosed;
    }

    @Override
    protected void onActorCloseRequested()
    {
        // timeout will no longer trigger, so we must fail the request here
        if (responseFuture != null && !responseFuture.isDone())
        {
            fail("Request closed", new RuntimeException());
        }
    }

    public void init(CompletableActorFuture<ClientResponse> responseFuture,
            Supplier<ActorFuture<RemoteAddress>> remoteAddressSupplier,
            Predicate<DirectBuffer> responseInspector,
            BufferWriter writer,
            Duration timeout)
    {
        // we want to do this in the client thread so that the client thread can be used for
        // marshalling the request.
        this.requestLength = writer.getLength();
        writer.write(requestBuffer, 0);

        actor.run(() ->
        {
            this.responseFuture = responseFuture;
            this.remoteAddressSupplier = remoteAddressSupplier;
            this.responseHandler = responseInspector;
            this.timeout = timeout;

            this.scheduledTimeout = actor.runDelayed(this.timeout, this::onRequestTimedOut);
            getNextRemoteAddress();
        });
    }

    private void getNextRemoteAddress()
    {
        // TODO: when to generate new request id? Only necessary if we actually send out the request
        requestId = requestIdGenerator.getNextRequestId();
        actor.runOnCompletion(remoteAddressSupplier.get(), this::onRemoteAddressReady);
    }

    private void onRemoteAddressReady(RemoteAddress address, Throwable throwable)
    {
        if (address != null)
        {
            LOG.trace("Next remote address obtained {}", address);

            if (remotesTried.isEmpty() || !address.equals(remotesTried.peek()))
            {
                remotesTried.push(address);
            }

            actor.runUntilDone(this::sendRequest);
        }
        else
        {
            LOG.trace("Did not obtain next remote addres, retrying");

            retryRequest();
        }
    }

    private void sendRequest()
    {
        final RemoteAddress remoteAddress = remotesTried.peek();
        final int requiredLength = RequestResponseHeaderDescriptor.framedLength(TransportHeaderDescriptor.framedLength(requestLength));

        long claimedOffset;

        do
        {
            claimedOffset = sendBuffer.claim(sendBufferClaim, requiredLength, remoteAddress.getStreamId());
        }
        while (claimedOffset == -2);

        if (claimedOffset >= 0)
        {
            actor.done();

            try
            {
                final MutableDirectBuffer buffer = sendBufferClaim.getBuffer();

                int writeOffset = sendBufferClaim.getOffset();

                transportHeaderDescriptor.wrap(buffer, writeOffset)
                    .putProtocolRequestReponse();

                writeOffset += TransportHeaderDescriptor.headerLength();

                requestResponseHeader.wrap(buffer, writeOffset)
                    .requestId(requestId);

                writeOffset += RequestResponseHeaderDescriptor.headerLength();

                buffer.putBytes(writeOffset, requestBuffer, 0, requestLength);

                sendBufferClaim.commit();

                LOG.trace("Request written to send buffer");
            }
            catch (Throwable e)
            {
                sendBufferClaim.abort();
                throw e;
            }
        }
        else
        {
            actor.yield();
        }
    }

    private void retryRequest()
    {
        LOG.trace("Retrying request.");

        actor.runDelayed(RESUBMIT_TIMEOUT, this::getNextRemoteAddress);
    }

    private void onRequestTimedOut()
    {
        if (!responseFuture.isDone())
        {
            try
            {
                LOG.trace("Request timed out");

                final StringBuilder errBuilder = new StringBuilder("Request timed out after ").append(timeout).append(".\nRemotes tried (in reverse order):\n");

                for (RemoteAddress remoteAddress : remotesTried)
                {
                    errBuilder.append("\t").append(remoteAddress).append("\n");
                }

                final String errorMessage = errBuilder.toString();
                responseFuture.completeExceptionally(errorMessage, new RequestTimeoutException(errorMessage));
            }
            finally
            {
                closeRequest();
            }
        }
    }

    protected void closeRequest()
    {
        LOG.trace("Request closed");

        try
        {
            responseFuture = null;
            remotesTried.clear();
            requestId = -1;
        }
        finally
        {
            onRequestClosed.accept(this);
        }
    }

    public void fail(String failure, Exception cause)
    {
        actor.run(() ->
        {
            if (responseFuture != null && responseFuture.isAwaitingResult())
            {
                try
                {
                    if (cause != null && cause instanceof NotConnectedException)
                    {
                        LOG.trace("Channel to remove {} not connected, retrying", remotesTried.peek());

                        retryRequest();
                    }
                    else
                    {
                        LOG.trace("Completing request exceptionally. {}", failure);

                        try
                        {
                            responseFuture.completeExceptionally(failure, cause);
                            scheduledTimeout.cancel();
                        }
                        finally
                        {
                            closeRequest();
                        }
                    }
                }
                catch (IllegalStateException e)
                {
                    // ignore; this exception is expected when the request was resolved by the sender
                    // in the meantime
                    Loggers.TRANSPORT_LOGGER.debug("Could not fail request future", e);
                }
            }
        });
    }

    public void failPendingRequestToRemote(RemoteAddress remote, String reason)
    {
        actor.run(() ->
        {
            if (remote.equals(remotesTried.peek()))
            {
                fail(reason, new TransportException(reason));
            }
        });
    }

    public ActorFuture<Void> closeActor()
    {
        return actor.close();
    }

    public void processResponse(DirectBuffer buff, int offset, int length)
    {
        final CompletableActorFuture<ClientResponse> responseFuture = this.responseFuture;

        if (responseFuture.isAwaitingResult())
        {
            responseBuffer.putBytes(0, buff, offset, length);
            responseBufferView.wrap(responseBuffer, 0, length);

            actor.run(() ->
            {
                if (responseHandler.test(responseBufferView))
                {
                    LOG.trace("Response inspector decided to retry request.");

                    retryRequest();
                }
                else
                {
                    LOG.trace("Completing request successfully.");

                    responseFuture.complete(new ClientResponseImpl(requestId,
                        remotesTried.peek(),
                        responseBufferView,
                        this::closeRequest));

                    scheduledTimeout.cancel();
                }
            });
        }
        else
        {
            LOG.trace("Dropping response, not awaiting response anymore.");
        }
    }

    public long getCurrentRequestId()
    {
        return requestId;
    }

    static class ClientResponseImpl implements ClientResponse
    {
        private final long requestId;
        private final RemoteAddress remoteAddress;
        private final DirectBuffer responseBuffer;
        private final Runnable onClose;
        private final AtomicBoolean isClosed = new AtomicBoolean(false);

        ClientResponseImpl(long requestId,
                RemoteAddress remoteAddress,
                DirectBuffer responseBuffer,
                Runnable onClose)
        {
            this.requestId = requestId;
            this.remoteAddress = remoteAddress;
            this.responseBuffer = responseBuffer;
            this.onClose = onClose;
        }

        @Override
        public RemoteAddress getRemoteAddress()
        {
            return remoteAddress;
        }

        @Override
        public long getRequestId()
        {
            return requestId;
        }

        @Override
        public DirectBuffer getResponseBuffer()
        {
            return responseBuffer;
        }

        @Override
        public void close()
        {
            if (isClosed.compareAndSet(false, true))
            {
                onClose.run();
            }
            else
            {
                throw new IllegalStateException("Response is already closed");
            }
        }
    }
}
