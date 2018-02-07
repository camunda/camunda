package io.zeebe.util.sched.future;

import java.util.concurrent.*;

import io.zeebe.util.sched.ActorJob;
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
    public boolean block(ActorJob job)
    {
        return false;
    }

}
