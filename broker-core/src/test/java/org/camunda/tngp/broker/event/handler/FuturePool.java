package org.camunda.tngp.broker.event.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FuturePool
{
    protected List<CompletableFuture<?>> futures = new ArrayList<>();

    public <T> CompletableFuture<T>  next()
    {
        final CompletableFuture<T> future = new CompletableFuture<>();
        futures.add(future);
        return future;
    }

    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> at(int index)
    {
        return (CompletableFuture<T>) futures.get(index);
    }
}