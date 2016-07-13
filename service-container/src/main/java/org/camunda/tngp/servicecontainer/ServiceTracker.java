package org.camunda.tngp.servicecontainer;

public interface ServiceTracker extends ServiceLifecycleObserver
{
    /**
     * notified for any service that is already started when the tracker is registered
     */
    <S> void onTrackerRegistration(ServiceName<S> name, Service<S> service);
}
