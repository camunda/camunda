package io.zeebe.servicecontainer;

import java.util.concurrent.CompletableFuture;

public interface AsyncContext
{
    CompletableFuture<Void> async();

    void async(CompletableFuture<?> future);

    void run(Runnable action);
}
