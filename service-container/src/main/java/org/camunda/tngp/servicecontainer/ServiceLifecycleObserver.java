package org.camunda.tngp.servicecontainer;

public interface ServiceLifecycleObserver
{

    <S> void onServiceStarted(ServiceName<S> name, Service<S> service);

    <S> void onServiceStopping(ServiceName<S> name, Service<S> service);
}
