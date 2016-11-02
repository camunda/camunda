package org.camunda.tngp.servicecontainer;

public interface ServiceStartContext extends AsyncContext
{
    String getName();

    <S> S getService(ServiceName<S> name);

    <S> S getService(String name, Class<S> type);

    <S> ServiceBuilder<S> createService(ServiceName<S> name, Service<S> service);
}
