package io.zeebe.util.sched;

import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

import io.zeebe.util.sched.future.ActorFuture;

public class FutureContinuationRunnable<T> implements Runnable
{
    final ActorTask task;
    final ActorFuture<T> future;
    final BiConsumer<T, Throwable> callback;
    final boolean ensureBlockedOnFuture;

    FutureContinuationRunnable(ActorTask task, ActorFuture<T> future, BiConsumer<T, Throwable> callback, boolean ensureBlockedOnFuture)
    {
        this.task = task;
        this.future = future;
        this.callback = callback;
        this.ensureBlockedOnFuture = ensureBlockedOnFuture;
    }

    @Override
    public void run()
    {
        try
        {
            if (!ensureBlockedOnFuture || task.awaitFuture == future)
            {
                final T res = future.get();
                callback.accept(res, null);
            }
            else
            {
                System.out.println("Not calling continuation future");
            }
        }
        catch (ExecutionException e)
        {
            callback.accept(null, e);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
}