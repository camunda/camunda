package org.camunda.tngp.servicecontainer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.camunda.tngp.servicecontainer.impl.ServiceContainerImpl;

public class ServiceBuilder<S>
{
    protected final ServiceContainerImpl serviceContainer;

    protected final ServiceName<S> name;
    protected final Service<S> service;
    protected ServiceName<?> groupName;

    protected Set<ServiceName<?>> dependencies = new HashSet<>();
    protected Map<ServiceName<?>, Injector<?>> injectedDependencies = new HashMap<>();
    protected Map<ServiceName<?>, ServiceGroupReference<?>> injectedReferences = new HashMap<>();

    public ServiceBuilder(ServiceName<S> name, Service<S> service, ServiceContainerImpl serviceContainer)
    {
        this.name = name;
        this.service = service;
        this.serviceContainer = serviceContainer;
    }

    public ServiceBuilder<S> group(ServiceName<?> groupName)
    {
        this.groupName = groupName;
        return this;
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

    public <T> ServiceBuilder<S> groupReference(ServiceName<T> groupName, ServiceGroupReference<T> injector)
    {
        injectedReferences.put(groupName, injector);
        return this;
    }

    public CompletableFuture<Void> install()
    {
        return serviceContainer.onServiceBuilt(this);
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

    public Map<ServiceName<?>, ServiceGroupReference<?>> getInjectedReferences()
    {
        return injectedReferences;
    }

    public ServiceName<?> getGroupName()
    {
        return groupName;
    }
}
