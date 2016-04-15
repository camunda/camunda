package org.camunda.tngp.broker.servicecontainer;

public interface ServiceListener
{
    <S> void onServiceStarted(ServiceName<S> name, S service);

    <S> void onServiceStopping(ServiceName<S> name, S service);
}
