package org.camunda.tngp.servicecontainer.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.tngp.servicecontainer.Service;
import org.camunda.tngp.servicecontainer.ServiceBuilder;
import org.camunda.tngp.servicecontainer.ServiceContainer;
import org.camunda.tngp.servicecontainer.ServiceListener;
import org.camunda.tngp.servicecontainer.ServiceName;

public class ServiceContainerImpl implements ServiceContainer
{
    protected List<Runnable> operations = new ArrayList<>();

    protected Map<ServiceName<?>, ServiceController> controllers = new HashMap<>();
    protected List<ServiceListener> listeners = new ArrayList<>();

    @Override
    public <S> ServiceBuilder<S> createService(ServiceName<S> name, Service<S> service)
    {
        return new ServiceBuilder<>(name, service, this);
    }

    public void serviceBuilt(ServiceBuilder<?> serviceBuilder)
    {
        for (ServiceName<?> dependency : serviceBuilder.getDependencies())
        {
            ServiceController dependentServiceController = controllers.get(dependency);
            if(dependentServiceController != null && dependentServiceController.dependencies.containsKey(serviceBuilder.getName()))
            {
                throw new RuntimeException("Circular dependency detected: " + serviceBuilder.getName()+ " <-> " + dependentServiceController.name + "." );
            }
        }

        controllers.put(serviceBuilder.getName(), new ServiceController(serviceBuilder, this));
    }

    public void serviceStopped(ServiceController serviceController)
    {

    }

    public void serviceFailed(ServiceController serviceController)
    {

    }

    @Override
    public void registerListener(ServiceListener listener)
    {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(ServiceListener listener)
    {
        this.listeners.remove(listener);
    }

    public void serviceStarted(ServiceController serviceController)
    {
        invokeOnServiceStarted(serviceController, listeners);
        invokeOnServiceStarted(serviceController, serviceController.listeners);
    }

    @SuppressWarnings("unchecked")
    private void invokeOnServiceStarted(ServiceController serviceController, List<ServiceListener> listeners)
    {
        if(listeners != null)
        {
            for(int i = 0; i < listeners.size(); i++)
            {
                final ServiceListener serviceListener = listeners.get(i);
                try
                {
                    serviceListener.onServiceStarted(serviceController.name, serviceController.service.get());
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    public void serviceStopping(ServiceController serviceController)
    {
        invokeOnServiceStopping(serviceController, listeners);
        invokeOnServiceStopping(serviceController, serviceController.listeners);
    }

    @SuppressWarnings("unchecked")
    private void invokeOnServiceStopping(ServiceController serviceController, List<ServiceListener> listeners)
    {
        if(listeners != null)
        {
            for(int i = 0; i < listeners.size(); i++)
            {
                final ServiceListener serviceListener = listeners.get(i);
                try
                {
                    serviceListener.onServiceStopping(serviceController.name, serviceController.service.get());
                }
                catch(Exception e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void remove(ServiceName<?> serviceName)
    {
        final ServiceController serviceController = controllers.get(serviceName);
        if(serviceController != null)
        {
            serviceController.remove();
            controllers.remove(serviceController);
        }
        else
        {
            throw new IllegalArgumentException("Cannot remove service "+serviceName+"; no such service");
        }
    }

    public void stop()
    {
        for (ServiceController controller : controllers.values())
        {
            controller.remove();
        }
    }

    @SuppressWarnings("unchecked")
    public <S> Service<S> getService(ServiceName<?> name)
    {
        final ServiceController serviceController = controllers.get(name);

        if(serviceController != null && serviceController.state == ServiceController.STARTED)
        {
            return serviceController.service;
        }
        else
        {
            return null;
        }
    }

}
