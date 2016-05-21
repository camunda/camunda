package org.camunda.tngp.broker.servicecontainer;

public interface ServiceContainer
{
    <S> ServiceBuilder<S> createService(ServiceName<S> name, Service<S> service);

    void registerListener(ServiceListener listener);

    void removeListener(ServiceListener listener);

    void remove(ServiceName<?> serviceName);

    void stop();
}