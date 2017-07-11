/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
