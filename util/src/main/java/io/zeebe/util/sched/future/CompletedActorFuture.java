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
package io.zeebe.util.sched.future;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.zeebe.util.sched.FutureUtil;

/**
 * Immutable future that is already completed.
 */
public class CompletedActorFuture<V> implements ActorFuture<V>
{
    private final V value;
    private final String failure;
    private final Throwable failureCause;

    private final boolean isCompletedExceptionally;

    public CompletedActorFuture(V value)
    {
        this.value = value;
        this.failure = null;
        this.failureCause = null;
        this.isCompletedExceptionally = false;
    }

    public CompletedActorFuture(String failure, Throwable failureCause)
    {
        this.value = null;
        this.failure = failure;
        this.failureCause = failureCause;
        this.isCompletedExceptionally = true;
    }

    public CompletedActorFuture(Throwable failure)
    {
        this(failure.getMessage(), failure);
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
        return true;
    }

    @Override
    public boolean isCompletedExceptionally()
    {
        return isCompletedExceptionally;
    }

    @Override
    public V get() throws InterruptedException, ExecutionException
    {
        if (isCompletedExceptionally)
        {
            throw new ExecutionException(failure, failureCause);
        }
        else
        {
            return value;
        }
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
    {
        return get();
    }

    @Override
    public void complete(V value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void completeExceptionally(String failure, Throwable throwable)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void completeExceptionally(Throwable throwable)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public V join()
    {
        return FutureUtil.join(this);
    }

    @Override
    public boolean block(Runnable onCompletion)
    {
        return false;
    }

    @Override
    public Throwable getException()
    {
        if (!isCompletedExceptionally)
        {
            throw new IllegalStateException("Cannot call getException(); future is not completed exceptionally.");
        }
        return failureCause;
    }

}
