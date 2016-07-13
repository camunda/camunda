package org.camunda.tngp.servicecontainer;

public interface ServiceContainer
{
    <S> ServiceBuilder<S> createService(ServiceName<S> name, Service<S> service);

    /**
     * Receives notifications of all services started after it has been registered with the {@link ServiceContainer}.
     */
    void registerListener(ServiceListener listener);

    void removeListener(ServiceListener listener);

    /**
     * Receives notifications of all services that already run when it is registered with the {@link ServiceContainer}
     * and notifications of all services that are started after that.
     */
    void registerTracker(ServiceTracker tracker);

    void removeTracker(ServiceTracker tracker);

    void remove(ServiceName<?> serviceName);

    void stop();
}