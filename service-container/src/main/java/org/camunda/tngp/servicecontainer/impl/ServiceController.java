package org.camunda.tngp.servicecontainer.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.tngp.servicecontainer.Injector;
import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceBuilder;
import org.camunda.tngp.servicecontainer.ServiceContext;
import org.camunda.tngp.servicecontainer.ServiceListener;
import org.camunda.tngp.servicecontainer.ServiceName;

@SuppressWarnings("rawtypes")
public class ServiceController implements ServiceListener, ServiceContext
{
    public static final int NEW = 0;
    public static final int RESOLVED = 1;
    public static final int UNRESOLVED = 2;
    public static final int FAILED = 3;
    public static final int STARTED = 4;
    public static final int STOPPING = 5;
    public static final int REMOVED = 6;

    protected int state = NEW;

    protected final ServiceName name;
    protected final Service service;
    protected final ServiceContainerImpl container;

    protected int missingDependencies;
    protected final Map<ServiceName<?>, Object> dependencies = new HashMap<>();
    protected final Map<ServiceName<?>, Injector<?>> injectors;
    protected final List<ServiceListener> listeners;

    public ServiceController(ServiceBuilder<?> builder, ServiceContainerImpl serviceContainer)
    {
        this.container = serviceContainer;
        this.service = builder.getService();
        this.name = builder.getName();
        this.injectors = builder.getInjectedDependencies();
        this.listeners = builder.getListeners();

        for (ServiceName<?> dependency : builder.getDependencies())
        {
            Service<?> service = serviceContainer.getService(dependency);
            if(service != null)
            {
                resolve(dependency, service.get());
            }
            else
            {
                resolve(dependency, null);
                missingDependencies++;
            }
        }

        this.container.registerListener(this);

        checkResolved();
    }

    @SuppressWarnings("unchecked")
    private void resolve(ServiceName<?> dependency, Object service)
    {
        this.dependencies.put(dependency, service);
        Injector injector = this.injectors.get(dependency);
        if(injector != null)
        {
            injector.setValue(service);
        }
    }

    @Override
    public <S> void onServiceStarted(ServiceName<S> name, S service)
    {
        if(state == NEW || state == UNRESOLVED)
        {
            if(dependencies.containsKey(name))
            {
                resolve(name, service);
                --missingDependencies;
                checkResolved();
            }
        }
    }

    protected void checkResolved()
    {
        if(missingDependencies == 0)
        {
            this.state = RESOLVED;
            start();
        }
    }

    @Override
    public <S> void onServiceStopping(ServiceName<S> name, S service)
    {
        if(dependencies.containsKey(name))
        {
            if(state == RESOLVED)
            {
                this.state = UNRESOLVED;
            }
            else if(state == STARTED)
            {
                stop();
            }

            resolve(name, null);
            ++missingDependencies;
        }
    }

    protected void start()
    {
        System.out.println("Starting "+name);

        try
        {
            this.service.start(this);
            this.state = STARTED;
            this.container.serviceStarted(this);
        }
        catch(Exception e)
        {
            System.err.println("Exception while starting service");
            e.printStackTrace();
            this.state = FAILED;
            this.container.serviceFailed(this);
        }
    }

    protected void stop()
    {

        this.state = STOPPING;
        this.container.serviceStopping(this);

        try
        {
            System.out.println("Stopping "+name);
            this.service.stop();
            this.state = UNRESOLVED;
        }
        catch(Exception e)
        {
            System.err.println("Exception while stopping service");
            e.printStackTrace();
            this.state = FAILED;
        }
        finally
        {
            this.container.serviceStopped(this);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> S getService(ServiceName<S> name)
    {
        return ((Service<S>)dependencies.get(name)).get();
    }

    @Override
    public <S> S getService(String name, Class<S> type)
    {
        return getService(ServiceName.newServiceName(name, type));
    }

    public void remove()
    {
        if(state == STARTED)
        {
            stop();
        }

        this.container.removeListener(this);
        this.state = REMOVED;
    }

    @Override
    public String getName()
    {
        return name.getName();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> ServiceBuilder<S> createService(ServiceName<S> name, Service<S> service)
    {
        return new ServiceBuilder<>(name, service, container)
                .dependency(this.name);
    }
}
