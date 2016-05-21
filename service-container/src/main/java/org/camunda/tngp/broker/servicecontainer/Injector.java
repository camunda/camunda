package org.camunda.tngp.broker.servicecontainer;

public class Injector<S>
{
    protected S value;

    public void setValue(S service)
    {
        this.value = service;
    }

    public S getValue()
    {
        return value;
    }
}
