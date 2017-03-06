package org.camunda.tngp.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class AsyncContext
{
    protected final ManyToOneConcurrentArrayQueue<Runnable> cmdQueue;
    protected final Consumer<Runnable> cmdConsumer = Runnable::run;

    public AsyncContext()
    {
        this(100);
    }

    public AsyncContext(int capacity)
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

    public int doWork()
    {
        return cmdQueue.drain(cmdConsumer);
    }

}
