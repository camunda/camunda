package org.camunda.tngp.broker.servicecontainer;

public interface ServiceContext
{
    <S> S getService(ServiceName<S> name);

    <S> S getService(String name, Class<S> type);

    String getName();

    <S> ServiceBuilder<S> createService(ServiceName<S> name, Service<S> service);
}
