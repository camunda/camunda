package org.camunda.tngp.servicecontainer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface ServiceContainer
{
    void start();

    <S> ServiceBuilder<S> createService(ServiceName<S> name, Service<S> service);

    CompletableFuture<Void> removeService(ServiceName<?> serviceName);

    void close(long awaitTime, TimeUnit timeUnit) throws TimeoutException, ExecutionException, InterruptedException;
}