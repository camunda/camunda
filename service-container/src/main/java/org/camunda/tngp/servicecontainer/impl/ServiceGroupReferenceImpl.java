package org.camunda.tngp.servicecontainer.impl;

import java.util.List;

import org.camunda.tngp.servicecontainer.ServiceGroupReference;

@SuppressWarnings("rawtypes")
public class ServiceGroupReferenceImpl
{
    protected final ServiceController referringService;
    protected final ServiceGroupReference injector;
    protected final ServiceGroup group;

    public ServiceGroupReferenceImpl(ServiceController referringService, ServiceGroupReference injector, ServiceGroup group)
    {
        this.referringService = referringService;
        this.injector = injector;
        this.group = group;
    }

    public ServiceController getReferringService()
    {
        return referringService;
    }

    public ServiceGroupReference getInjector()
    {
        return injector;
    }

    public ServiceGroup getGroup()
    {
        return group;
    }

    public void remove()
    {
        group.removeReference(this);
    }

    @SuppressWarnings("unchecked")
    public void injectInitialValues()
    {
        final List<ServiceController> controllers = group.getControllers();

        for (ServiceController ctr : controllers)
        {
            if (ctr.isStarted())
            {
                final Object value = ctr.service.get();
                injector.addValue(ctr.name, value);
            }
        }
    }

    public void uninject()
    {
        injector.uninject();
    }
}
