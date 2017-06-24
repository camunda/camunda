package io.zeebe.transport.impl;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Consumer;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

import io.zeebe.dispatcher.ClaimedFragment;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.transport.ClientRequest;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.transport.RequestTimeoutException;
import io.zeebe.transport.impl.ClientRequestPool.RequestIdGenerator;
import io.zeebe.util.buffer.BufferWriter;

public class ClientRequestImpl implements ClientRequest
{
    private static final AtomicIntegerFieldUpdater<ClientRequestImpl> STATE_FIELD = AtomicIntegerFieldUpdater.newUpdater(ClientRequestImpl.class, "state");

    private static final int CLOSED = 1;
    private static final int AWAITING_RESPONSE = 2;
    private static final int RESPONSE_AVAILABLE = 3;
    private static final int FAILED = 5;
    private static final int TIMED_OUT = 4;

    @SuppressWarnings("unused") // used through STATE_FIELD
    private volatile int state = CLOSED;

    private final TransportHeaderDescriptor transportHeaderDescriptor = new TransportHeaderDescriptor();
    private final RequestResponseHeaderDescriptor requestResponseHeader = new RequestResponseHeaderDescriptor();

    private final Consumer<ClientRequestImpl> closeHandler;
    private final RequestIdGenerator requestIdGenerator;
    private final Dispatcher sendBuffer;
    private final ClaimedFragment sendBufferClaim = new ClaimedFragment();

    private volatile long requestId;
    private RemoteAddress remoteAddress;
    protected Exception failure;

    private final MutableDirectBuffer responseBuffer = new ExpandableArrayBuffer();
    private final UnsafeBuffer responseBufferView = new UnsafeBuffer(0, 0);

    private final IdleStrategy awaitResponseStreategy = new BackoffIdleStrategy(1000, 100, 1, TimeUnit.MILLISECONDS.toNanos(1));

    public ClientRequestImpl(RequestIdGenerator requestIdGenerator, Dispatcher sendBuffer, Consumer<ClientRequestImpl> closeHandler)
    {
        this.requestIdGenerator = requestIdGenerator;
        this.sendBuffer = sendBuffer;
        this.closeHandler = closeHandler;
    }

    public boolean open(RemoteAddress remoteAddress, BufferWriter writer)
    {
        this.remoteAddress = remoteAddress;
        this.requestId = requestIdGenerator.getNextRequestId();

        final int requiredLength = RequestResponseHeaderDescriptor.framedLength(TransportHeaderDescriptor.framedLength(writer.getLength()));

        long claimedOffset = -2;

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

                STATE_FIELD.set(this, AWAITING_RESPONSE);

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
        final int prevState = STATE_FIELD.getAndSet(this, CLOSED);

        if (prevState != CLOSED)
        {
            remoteAddress = null;
            requestId = -1;
            closeHandler.accept(this);
        }
    }

    public void fail(Exception e)
    {
        if (STATE_FIELD.compareAndSet(this, AWAITING_RESPONSE, FAILED))
        {
            failure = e;
        }
    }

    @Override
    public long getRequestId()
    {
        return requestId;
    }

    public void processResponse(DirectBuffer buff, int offset, int length)
    {
        if (STATE_FIELD.get(this) == AWAITING_RESPONSE)
        {
            responseBuffer.putBytes(0, buff, offset, length);
            responseBufferView.wrap(responseBuffer, 0, length);

            STATE_FIELD.compareAndSet(this, AWAITING_RESPONSE, RESPONSE_AVAILABLE);
        }
    }



    @Override
    public DirectBuffer get() throws InterruptedException, ExecutionException
    {
        try
        {
            return get(30, TimeUnit.SECONDS);
        }
        catch (TimeoutException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DirectBuffer join()
    {
        try
        {
            return get();
        }
        catch (InterruptedException | ExecutionException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isCancelled()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDone()
    {
        return STATE_FIELD.get(this) != AWAITING_RESPONSE;
    }

    public boolean isAwaitingResponse()
    {
        return STATE_FIELD.get(this) == AWAITING_RESPONSE;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public DirectBuffer get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
    {
        awaitResponseStreategy.reset();

        final long maxWait = System.nanoTime() + unit.toNanos(timeout);

        do
        {
            final int state = STATE_FIELD.get(this);

            switch (state)
            {
                case RESPONSE_AVAILABLE:
                    return responseBufferView;

                case TIMED_OUT:
                    throw new ExecutionException(new RequestTimeoutException());

                case CLOSED:
                    throw new ExecutionException(new RuntimeException("Request closed; If you see this exception, you should no longer hold this object (reuse)"));

                case FAILED:
                    throw new ExecutionException("Request failed", failure);

                default:
                    awaitResponseStreategy.idle();
                    break;
            }

            if (System.nanoTime() >= maxWait)
            {
                STATE_FIELD.compareAndSet(this, AWAITING_RESPONSE, TIMED_OUT);
            }
        }
        while (true);
    }
}
