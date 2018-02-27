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

import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.impl.ClientRequestPool.RequestIdGenerator;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.ActorTask;
import io.zeebe.util.sched.future.CompletableActorFuture;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

public class ClientRequestImpl implements ClientRequest
{
    private final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    private final RequestResponseHeaderDescriptor requestResponseHeader = new RequestResponseHeaderDescriptor();

    private final Consumer<ClientRequestImpl> closeHandler;
    private final RequestIdGenerator requestIdGenerator;
    private final Dispatcher sendBuffer;
    private final ClaimedFragment sendBufferClaim = new ClaimedFragment();

    private volatile long requestId;
    private RemoteAddress remoteAddress;
    private final CompletableActorFuture<DirectBuffer> responseFuture = new CompletableActorFuture<>();

    private final MutableDirectBuffer responseBuffer = new ExpandableArrayBuffer();
    private final UnsafeBuffer responseBufferView = new UnsafeBuffer(0, 0);

    public ClientRequestImpl(RequestIdGenerator requestIdGenerator, Dispatcher sendBuffer, Consumer<ClientRequestImpl> closeHandler)
    {
        this.requestIdGenerator = requestIdGenerator;
        this.sendBuffer = sendBuffer;
        this.closeHandler = closeHandler;
    }

    public void init(RemoteAddress remoteAddress)
    {
        this.responseFuture.setAwaitingResult();
        this.requestId = requestIdGenerator.getNextRequestId();
        this.remoteAddress = remoteAddress;
    }

    public boolean submit(BufferWriter writer)
    {
        this.responseFuture.setAwaitingResult();
        final int requiredLength = RequestResponseHeaderDescriptor.framedLength(TransportHeaderDescriptor.framedLength(writer.getLength()));

        long claimedOffset;

        do
        {
            claimedOffset = sendBuffer.claim(sendBufferClaim, requiredLength, remoteAddress.getStreamId());
        }
        while (claimedOffset == -2);

        if (claimedOffset >= 0)
        {
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

                writer.write(buffer, writeOffset);

                sendBufferClaim.commit();

                return true;
            }
            catch (Throwable e)
            {
                sendBufferClaim.abort();
                throw e;
            }
        }
        else
        {
            return false;
        }
    }

    public RemoteAddress getRemoteAddress()
    {
        return remoteAddress;
    }

    @Override
    public void close()
    {
        final boolean nowClosed = responseFuture.close();

        if (nowClosed)
        {
            remoteAddress = null;
            requestId = -1;
            closeHandler.accept(this);
        }
    }

    public void fail(String failure, Exception cause)
    {
        responseFuture.completeExceptionally(failure, cause);
    }

    @Override
    public long getRequestId()
    {
        return requestId;
    }

    public void processResponse(DirectBuffer buff, int offset, int length)
    {
        if (responseFuture.isAwaitingResult())
        {
            responseBuffer.putBytes(0, buff, offset, length);
            responseBufferView.wrap(responseBuffer, 0, length);
            responseFuture.complete(responseBufferView);
        }
    }

    @Override
    public DirectBuffer get() throws ExecutionException
    {
        return responseFuture.get();
    }

    @Override
    public DirectBuffer join()
    {
        return responseFuture.join();
    }

    @Override
    public boolean isCancelled()
    {
        return responseFuture.isCancelled();
    }

    @Override
    public boolean isDone()
    {
        return responseFuture.isDone();
    }

    public boolean isAwaitingResponse()
    {
        return responseFuture.isAwaitingResult();
    }

    @Override
    public boolean isFailed()
    {
        return responseFuture.isCompletedExceptionally();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public DirectBuffer get(long timeout, TimeUnit unit) throws ExecutionException, TimeoutException
    {
        return responseFuture.get(timeout, unit);
    }

    @Override
    public void complete(DirectBuffer value)
    {
        new UnsupportedOperationException();
    }

    @Override
    public void completeExceptionally(String failure, Throwable throwable)
    {
        new UnsupportedOperationException();
    }

    @Override
    public void completeExceptionally(Throwable throwable)
    {
        new UnsupportedOperationException();
    }

    @Override
    public void block(ActorTask onCompletion)
    {
        responseFuture.block(onCompletion);
    }

    @Override
    public boolean isCompletedExceptionally()
    {
        return responseFuture.isCompletedExceptionally();
    }

    @Override
    public Throwable getException()
    {
        return responseFuture.getException();
    }
}
