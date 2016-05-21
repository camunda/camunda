package org.camunda.tngp.broker.servicecontainer;

public interface Service<S>
{
    void start(ServiceContext serviceContext);

    void stop();

    S get();
}
