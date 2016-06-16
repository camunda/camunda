package org.camunda.tngp.broker.transport.worker.spi;

public interface ResourceContextProvider<C extends ResourceContext>
{
    C getContextForResource(int id);

    C[] getContexts();
}
