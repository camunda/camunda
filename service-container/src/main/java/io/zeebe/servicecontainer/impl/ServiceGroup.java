package io.zeebe.servicecontainer.impl;

import java.util.ArrayList;
import java.util.List;

import io.zeebe.servicecontainer.ServiceName;

public class ServiceGroup
{
    protected final ServiceName<?> groupName;

    protected final List<ServiceController> controllers = new ArrayList<>();
    protected final List<ServiceGroupReferenceImpl> references = new ArrayList<ServiceGroupReferenceImpl>();

    public ServiceGroup(ServiceName<?> groupName)
    {
        this.groupName = groupName;
    }

    public void addReference(ServiceGroupReferenceImpl reference)
    {
        references.add(reference);
    }

    public void removeReference(ServiceGroupReferenceImpl reference)
    {
        references.remove(reference);
    }

    public void addService(ServiceController controller)
    {
        controllers.add(controller);
        onServiceAdded(controller);
    }

    public void removeService(ServiceController controller)
    {
        controllers.remove(controller);
        onServiceRemoved(controller);
    }

    private void onServiceAdded(ServiceController controller)
    {
        for (int i = 0; i < references.size(); i++)
        {
            final ServiceGroupReferenceImpl reference = references.get(i);
            final ServiceController referringService = reference.getReferringService();

            referringService.onReferencedServiceStart(reference, controller);
        }
    }

    private void onServiceRemoved(ServiceController controller)
    {
        for (int i = 0; i < references.size(); i++)
        {
            final ServiceGroupReferenceImpl reference = references.get(i);
            final ServiceController referringService = reference.getReferringService();

            referringService.onReferencedServiceStop(reference, controller);
        }
    }

    public List<ServiceController> getControllers()
    {
        return controllers;
    }
}
