package org.camunda.tngp.servicecontainer;

public interface Service<S>
{
    void start(ServiceContext serviceContext);

    void stop();

    S get();
}
