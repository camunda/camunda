package org.camunda.tngp.transport.protocol.client;

import static java.util.concurrent.TimeUnit.*;
import static org.camunda.tngp.transport.protocol.TransportRequestHeaderDescriptor.*;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.transport.TransportChannel;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.BackoffIdleStrategy;
import uk.co.real_logic.agrona.concurrent.IdleStrategy;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

/**
 * reusable request implementation
 */
public class TransportRequestImpl implements TransportRequest
{
    public final static int STATE_CLOSED = 0;
    public final static int STATE_OPENING = 1;
    public final static int STATE_OPEN = 2;
    public final static int STATE_RESPONSE_AVAILABLE = 3;
    public final static int STATE_FAILED = 4;
    public final static int STATE_TIMED_OUT = 5;

    public final static AtomicIntegerFieldUpdater<TransportRequestImpl> STATE_FIELD
        = AtomicIntegerFieldUpdater.newUpdater(TransportRequestImpl.class, "state");

    protected volatile int state;

    protected final UnsafeBuffer requestBuffer = new UnsafeBuffer(0,0);

    protected final ClaimedFragment claimedFragment = new ClaimedFragment();

    protected final UnsafeBuffer responseBuffer = new UnsafeBuffer(0,0);

    protected final IdleStrategy responseAwaitIdleStrategy;

    protected int responseLength;

    protected long id;

    protected long requestTimeout;

    protected TransportConnectionImpl connection;

    protected int channelId;

    protected long requestTime;

    protected long connectionId;

    public TransportRequestImpl(int responseBufferSize, long requestTimeoutMillis)
    {
        this.responseBuffer.wrap(ByteBuffer.allocateDirect(responseBufferSize));
        this.requestTimeout = requestTimeoutMillis;
        this.responseAwaitIdleStrategy = new BackoffIdleStrategy(2000, 200, 2000, MILLISECONDS.toNanos(5));

        STATE_FIELD.set(this, STATE_CLOSED);
    }

    public void begin(
            final TransportConnectionImpl connection,
            final long requestId,
            final int channelId,
            final long now)
    {
        if(STATE_FIELD.compareAndSet(this, STATE_CLOSED, STATE_OPENING))
        {
            this.connection = connection;
            this.connectionId = connection.getId();
            this.id = requestId;
            this.channelId = channelId;
            this.requestTime = now;
            // fields above are visible to concurrent threads only after commit();
        }
        else
        {
            throw new IllegalStateException("Cannot open request, has not been closed.");
        }
    }

    public void commit()
    {
        if(STATE_FIELD.compareAndSet(this, STATE_OPENING, STATE_OPEN))
        {
            if(claimedFragment.isOpen())
            {
                claimedFragment.commit();
            }
        }
        else
        {
            abort();
            throw new IllegalStateException("Cannot finish opening request.");
        }
    }

    public void abort()
    {
        if(claimedFragment.isOpen())
        {
            claimedFragment.abort();
        }

        close();
    }


    public void close()
    {
        // close() should be idempotent and failsafe
        if(connection != null)
        {
            connection.onRequestClosed(this);
        }

        this.connection = null;
        this.connectionId = -1;
        this.id = -1;
        this.channelId = -1;
        this.responseLength = -1;

        STATE_FIELD.set(this, STATE_CLOSED);
    }

    public void writeHeader()
    {
        final MutableDirectBuffer claimedBuffer = claimedFragment.getBuffer();
        final int headerOffset = claimedFragment.getOffset();
        claimedBuffer.putLong(connectionIdOffset(headerOffset), connectionId);
        claimedBuffer.putLong(requestIdOffset(headerOffset), id);
    }

    @Override
    public boolean pollResponse()
    {
        return pollResponse(System.currentTimeMillis());
    }

    public boolean pollResponse(long now)
    {
        final int state = STATE_FIELD.get(this);

        boolean isResponseAvailable = false;

        if(state == STATE_RESPONSE_AVAILABLE)
        {
            isResponseAvailable = true;
        }
        else if(state == STATE_FAILED)
        {
            throw new RuntimeException("Request failed, channel closed");
        }
        else if(state == STATE_OPEN)
        {
            if(requestTime + requestTimeout < now)
            {
                if(STATE_FIELD.compareAndSet(this, STATE_OPEN, STATE_TIMED_OUT))
                {
                    throw new RuntimeException("Response timed out");
                }
            }
        }
        else if(state == STATE_CLOSED)
        {
            throw new IllegalStateException("Cannot poll response, request is closed.");
        }

        return isResponseAvailable;
    }

    /**
     * blocks until either
     * - the response is available,
     * - the request's timeout is reached
     * - the provided timeout is met.
     *
     * If the request's timeout is reached, the method throws a {@link RuntimeException}
     *
     * @throws RuntimeException if the request's timeout is reached.
     */
    @Override
    public boolean awaitResponse(long timeout, TimeUnit timeUnit)
    {
        boolean isResponseAvailable = pollResponse();

        if(!isResponseAvailable)
        {
            final long endAwait = System.currentTimeMillis() + timeUnit.toMillis(timeout);

            responseAwaitIdleStrategy.reset();
            do
            {
                responseAwaitIdleStrategy.idle();
                isResponseAvailable = pollResponse();
            }
            while(!isResponseAvailable && System.currentTimeMillis() < endAwait);
        }

        return isResponseAvailable;
    }

    @Override
    public void awaitResponse()
    {
        awaitResponse(30, TimeUnit.SECONDS);
    }

    public long getId()
    {
        return id;
    }

    public ClaimedFragment getClaimedFragment()
    {
        return claimedFragment;
    }

    @Override
    public MutableDirectBuffer getRequestBuffer()
    {
        return requestBuffer;
    }

    @Override
    public MutableDirectBuffer getClaimedRequestBuffer()
    {
        return claimedFragment.getBuffer();
    }

    @Override
    public int getClaimedOffset()
    {
        return claimedFragment.getOffset() + headerLength();
    }

    @Override
    public long getRequestTimeout()
    {
        return requestTimeout;
    }

    @Override
    public DirectBuffer getResponseBuffer()
    {
        return responseBuffer;
    }

    public int getResponseLength()
    {
        return responseLength;
    }

    public void processChannelClosed(TransportChannel transportChannel)
    {
        if(STATE_FIELD.get(this) == STATE_OPEN)
        {
            if(transportChannel.getId() == channelId)
            {
                STATE_FIELD.compareAndSet(this, STATE_OPEN, STATE_FAILED);
            }
        }
    }

    public boolean processSendError(long requestId)
    {
        boolean isHandled = false;

        if(STATE_FIELD.get(this) == STATE_OPEN)
        {
            if(id == requestId)
            {
                isHandled = STATE_FIELD.compareAndSet(this, STATE_OPEN, STATE_FAILED);
            }
        }

        return isHandled;
    }

    @Override
    public boolean isOpen()
    {
        return STATE_FIELD.get(this) == STATE_OPEN;
    }

    public boolean processResponse(DirectBuffer buffer, int offset, int length, long requestId)
    {
        boolean isResponseHandled = false;

        if(isOpen())
        {
            if(id == requestId)
            {
                this.responseLength = length - headerLength();

                if(responseBuffer.capacity() < responseLength)
                {
                    responseBuffer.wrap(ByteBuffer.allocateDirect(responseLength));
                }

                responseBuffer.putBytes(0, buffer, offset + headerLength(), responseLength);

                // broadcast
                isResponseHandled = STATE_FIELD.compareAndSet(this, STATE_OPEN, STATE_RESPONSE_AVAILABLE);
            }
        }

        return isResponseHandled;
    }
}
