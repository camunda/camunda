package org.camunda.tngp.servicecontainer;

public class Injector<S>
{
    protected S value;
    protected ServiceName<S> injectedServiceName;

    public void inject(S service)
    {
        this.value = service;
    }

    public void uninject()
    {
        this.value = null;
    }

    public S getValue()
    {
        return value;
    }

    public ServiceName<S> getInjectedServiceName()
    {
        return injectedServiceName;
    }

    public void setInjectedServiceName(ServiceName<S> injectedServiceName)
    {
        this.injectedServiceName = injectedServiceName;
    }
}
