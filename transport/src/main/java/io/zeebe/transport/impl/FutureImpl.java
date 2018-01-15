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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.UnsafeBuffer;

public class FutureImpl implements Future<DirectBuffer>
{

    private static final AtomicIntegerFieldUpdater<FutureImpl> STATE_FIELD = AtomicIntegerFieldUpdater.newUpdater(FutureImpl.class, "state");

    private static final int AWAITING_RESULT = 1;
    private static final int RESULT_AVAILABLE = 2;
    private static final int FAILED = 3;
    private static final int CLOSED = 4;

    @SuppressWarnings("unused") // used through STATE_FIELD
    private volatile int state = CLOSED;

    private final MutableDirectBuffer responseBuffer = new ExpandableArrayBuffer();
    private final UnsafeBuffer responseBufferView = new UnsafeBuffer(0, 0);

    private final IdleStrategy awaitResponseStrategy = new BackoffIdleStrategy(1000, 100, 1, TimeUnit.MILLISECONDS.toNanos(1));

    protected String failure;
    protected Exception failureCause;

    public void awaitResult()
    {
        STATE_FIELD.set(this, AWAITING_RESULT);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCancelled()
    {
        return false;
    }

    @Override
    public boolean isDone()
    {
        return STATE_FIELD.get(this) != AWAITING_RESULT;
    }

    public boolean isFailed()
    {
        return STATE_FIELD.get(this) == FAILED;
    }

    public boolean isAwaitingResult()
    {
        return STATE_FIELD.get(this) == AWAITING_RESULT;
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
    public DirectBuffer get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException
    {
        awaitResponseStrategy.reset();

        final long maxWait = System.nanoTime() + unit.toNanos(timeout);

        do
        {
            final int state = STATE_FIELD.get(this);

            switch (state)
            {
                case RESULT_AVAILABLE:
                    return responseBufferView;

                case CLOSED:
                    throw new ExecutionException(new RuntimeException("Future closed; If you see this exception, you should no longer hold this object (reuse)"));

                case FAILED:
                    throw new ExecutionException(failure, failureCause);

                default:
                    awaitResponseStrategy.idle();
                    break;
            }

            if (System.nanoTime() >= maxWait)
            {
                throw new TimeoutException();
            }
        }
        while (true);
    }

    public void complete(DirectBuffer buff, int offset, int length)
    {
        if (STATE_FIELD.get(this) == AWAITING_RESULT)
        {
            responseBuffer.putBytes(0, buff, offset, length);
            responseBufferView.wrap(responseBuffer, 0, length);

            STATE_FIELD.compareAndSet(this, AWAITING_RESULT, RESULT_AVAILABLE);
        }
    }

    public void fail(String failure, Exception cause)
    {
        if (STATE_FIELD.compareAndSet(this, AWAITING_RESULT, FAILED))
        {
            this.failure = failure;
            this.failureCause = cause;
        }
    }

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


    public boolean close()
    {
        final int prevState = STATE_FIELD.getAndSet(this, CLOSED);

        if (prevState != CLOSED)
        {
            failure = null;
            failureCause = null;
        }

        return prevState != CLOSED;
    }

    public boolean isClosed()
    {
        return STATE_FIELD.get(this) == CLOSED;
    }

}
