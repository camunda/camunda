package org.camunda.tngp.broker.transport.worker.spi;

public interface ResourceContext
{
    int getResourceId();
    String getResourceName();
}
