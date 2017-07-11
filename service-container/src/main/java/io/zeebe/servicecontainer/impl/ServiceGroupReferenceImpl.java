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

import java.util.List;

import io.zeebe.servicecontainer.ServiceGroupReference;

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
