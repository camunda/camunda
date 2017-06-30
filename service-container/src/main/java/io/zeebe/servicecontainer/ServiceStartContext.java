package io.zeebe.servicecontainer;

import java.util.concurrent.CompletableFuture;

public interface ServiceStartContext extends AsyncContext
{
    String getName();

    <S> S getService(ServiceName<S> name);

    <S> S getService(String name, Class<S> type);

    <S> ServiceBuilder<S> createService(ServiceName<S> name, Service<S> service);

    <S> CompletableFuture<Void> removeService(ServiceName<S> name);
}
