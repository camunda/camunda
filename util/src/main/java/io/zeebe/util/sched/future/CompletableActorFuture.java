package io.zeebe.util.sched.future;

import static org.agrona.UnsafeAccess.UNSAFE;

import java.util.Queue;
import java.util.concurrent.*;

import io.zeebe.util.sched.*;
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import org.agrona.concurrent.ManyToOneConcurrentLinkedQueue;

/** Completable future implementation that is garbage free and reusable */
@SuppressWarnings("restriction")
public class CompletableActorFuture<V> implements ActorFuture<V>
{
    private static final long STATE_OFFSET;

    private static final int AWAITING_RESULT = 1;
    private static final int COMPLETING = 2;
    private static final int RESULT_AVAILABLE = 3;
    private static final int FAILED = 4;
    private static final int CLOSED = 5;

    private final ManyToOneConcurrentArrayQueue<ActorJob> blockedTasks = new ManyToOneConcurrentArrayQueue<>(32);

    /** used when blocked tasks has reached capacity (this queue has no capacity restriction but is not garbage free) */
    private final ManyToOneConcurrentLinkedQueue<ActorJob> blockedTasksOverflow = new ManyToOneConcurrentLinkedQueue<>();

    private volatile int state = CLOSED;

    protected V value;
    protected String failure;
    protected Throwable failureCause;

    public CompletableActorFuture()
    {
        setAwaitingResult();
    }

    public void setAwaitingResult()
    {
        state = AWAITING_RESULT;
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
        final int state = this.state;
        return state == RESULT_AVAILABLE || state == FAILED;
    }

    public boolean isFailed()
    {
        return state == FAILED;
    }

    public boolean isAwaitingResult()
    {
        return state == AWAITING_RESULT;
    }

    @Override
    public boolean block(ActorJob job)
    {
        if (!blockedTasks.add(job))
        {
            blockedTasksOverflow.add(job);
        }

        return !isDone();
    }

    @Override
    public V get() throws InterruptedException, ExecutionException
    {
        try
        {
            return get(Integer.MAX_VALUE, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
    {
        if (!isDone())
        {
            if (ActorTaskRunner.current() != null)
            {
                throw new IllegalStateException(
                        "Actor call get() on future which has not completed. " + "Actors must be non-blocking. Use actor.awaitFuture().");
            }
            else
            {
                final long waitTime = System.currentTimeMillis() + unit.toMillis(timeout) + 1;
                // TODO: better implementation of blocking for non-actor threads
                while (!isDone())
                {
                    if (System.currentTimeMillis() > waitTime)
                    {
                        throw new TimeoutException();
                    }

                    Thread.yield();
                }
            }
        }

        if (isFailed())
        {
            throw new ExecutionException(failure, failureCause);
        }
        else
        {
            return value;
        }
    }


    @Override
    public void complete(V value)
    {
        if (UNSAFE.compareAndSwapInt(this, STATE_OFFSET, AWAITING_RESULT, COMPLETING))
        {
            this.value = value;
            this.state = RESULT_AVAILABLE;
            notifyBlockedTasks();
        }
        else
        {
            throw new IllegalStateException("Cannot complete future, the future is already completed.");
        }
    }

    @Override
    public void completeExceptionally(String failure, Throwable throwable)
    {
        if (UNSAFE.compareAndSwapInt(this, STATE_OFFSET, AWAITING_RESULT, COMPLETING))
        {
            this.failure = failure;
            this.failureCause = throwable;
            this.state = FAILED;
            notifyBlockedTasks();
        }
        else
        {
            throw new IllegalStateException("Cannot complete future, the future is already completed.");
        }
    }

    @Override
    public void completeExceptionally(Throwable throwable)
    {
        completeExceptionally(throwable.getMessage(), throwable);
    }

    private void notifyBlockedTasks()
    {
        notifyAllInQueue(blockedTasks);
        notifyAllInQueue(blockedTasksOverflow);
    }

    private void notifyAllInQueue(Queue<ActorJob> tasks)
    {
        while (!tasks.isEmpty())
        {
            final ActorJob blocked = tasks.poll();
            if (blocked != null)
            {
                try
                {
                    blocked.onFutureCompleted();
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }

    }

    @Override
    public V join()
    {
        return FutureUtil.join(this);
    }

    /** future is reusable after close */
    public boolean close()
    {
        final int prevState = UNSAFE.getAndSetInt(this, STATE_OFFSET, CLOSED);

        if (prevState != CLOSED)
        {
            value = null;
            failure = null;
            failureCause = null;
            notifyBlockedTasks();
        }

        return prevState != CLOSED;
    }

    public boolean isClosed()
    {
        return state == CLOSED;
    }

    static
    {
        try
        {
            STATE_OFFSET = UNSAFE.objectFieldOffset(CompletableActorFuture.class.getDeclaredField("state"));
        }
        catch (final Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
}
