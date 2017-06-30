package io.zeebe.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class DeferredCommandContext
{
    protected final ManyToOneConcurrentArrayQueue<Runnable> cmdQueue;
    protected final Consumer<Runnable> cmdConsumer = Runnable::run;

    public DeferredCommandContext()
    {
        this(100);
    }

    public DeferredCommandContext(int capacity)
    {
        this.cmdQueue = new ManyToOneConcurrentArrayQueue<>(capacity);
    }

    public <T> CompletableFuture<T> runAsync(Consumer<CompletableFuture<T>> action)
    {
        final CompletableFuture<T> future = new CompletableFuture<>();

        cmdQueue.add(() ->
        {
            try
            {
                action.accept(future);
            }
            catch (Exception e)
            {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Use this when no future is required.
     */
    public void runAsync(Runnable r)
    {
        cmdQueue.add(r);
    }

    public int doWork()
    {
        return cmdQueue.drain(cmdConsumer);
    }

}
