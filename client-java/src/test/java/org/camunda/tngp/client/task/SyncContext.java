package org.camunda.tngp.client.task;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.camunda.tngp.util.AsyncContext;

public class SyncContext extends AsyncContext
{

    /**
     * Doing everything immediately
     */
    @Override
    public <T> CompletableFuture<T> runAsync(Consumer<CompletableFuture<T>> action)
    {
        final CompletableFuture<T> future = new CompletableFuture<>();

        try
        {
            action.accept(future);
        }
        catch (Exception e)
        {
            future.completeExceptionally(e);
        }

        return future;
    }

}
