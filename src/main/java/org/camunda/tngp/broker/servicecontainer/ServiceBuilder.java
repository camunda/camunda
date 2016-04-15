package org.camunda.tngp.broker.servicecontainer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.camunda.tngp.broker.servicecontainer.impl.ServiceContainerImpl;

public class ServiceBuilder<S>
{
    protected final ServiceName<S> name;
    protected final Service<S> service;
    protected Set<ServiceName<?>> dependencies = new HashSet<>();
    protected Map<ServiceName<?>, Injector<?>> injectedDependencies = new HashMap<>();
    protected ServiceContainerImpl serviceContainer;
    protected List<ServiceListener> listeners;

    public ServiceBuilder(ServiceName<S> name, Service<S> service, ServiceContainerImpl serviceContainer)
    {
        this.name = name;
        this.service = service;
        this.serviceContainer = serviceContainer;
    }

    public <T> ServiceBuilder<S> dependency(ServiceName<T> serviceName)
    {
        dependencies.add(serviceName);
        return this;
    }

    public <T> ServiceBuilder<S> dependency(ServiceName<T> serviceName, Injector<T> injector)
    {
        injectedDependencies.put(serviceName, injector);
        return dependency(serviceName);
    }

    public <T> ServiceBuilder<S> dependency(String name, Class<T> type)
    {
        return dependency(ServiceName.newServiceName(name, type));
    }

    public ServiceName<S> done()
    {
        serviceContainer.serviceBuilt(this);
        return name;
    }

    public ServiceName<S> getName()
    {
        return name;
    }

    public Service<S> getService()
    {
        return service;
    }

    public Set<ServiceName<?>> getDependencies()
    {
        return dependencies;
    }

    public Map<ServiceName<?>, Injector<?>> getInjectedDependencies()
    {
        return injectedDependencies;
    }

    public ServiceBuilder<?> listener(ServiceListener listener)
    {
        if(listeners == null)
        {
            listeners = new ArrayList<>();
        }
        listeners.add(listener);
        return this;
    }

    public List<ServiceListener> getListeners()
    {
        return listeners;
    }

}
