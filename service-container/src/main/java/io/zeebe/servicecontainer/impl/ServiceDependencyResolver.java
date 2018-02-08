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

import java.util.*;

import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.impl.ServiceEvent.ServiceEventType;
import org.slf4j.Logger;

/**
 * Stream processor tracking the dependencies of services.
 */
public class ServiceDependencyResolver
{
    public static final Logger LOG = Loggers.SERVICE_CONTAINER_LOGGER;

    /** all services installed into the container, indexed by name */
    private final Map<ServiceName<?>, ServiceController> installedServices = new HashMap<>();

    /** map of service names which have not been resolved yet */
    private final Map<ServiceName<?>, List<ServiceController>> unresolvedDependencies = new HashMap<>();

    /** map of services and their resolved dependencies */
    private final Map<ServiceController, List<ServiceController>> resolvedDependencies = new HashMap<>();

    /** map of services and the services which depend on them */
    private final Map<ServiceController, List<ServiceController>> dependentServices = new HashMap<>();

    /** set of services which have started */
    private final Set<ServiceController> startedServices = new HashSet<>();

    /** set of services which are currently stopping */
    private final Set<ServiceController> stoppingServices = new HashSet<>();

    /** map of service groups */
    private final Map<ServiceName<?>, ServiceGroup> groups = new HashMap<>();

    public void onServiceEvent(ServiceEvent event)
    {
        switch (event.getType())
        {
            case SERVICE_INSTALLED:
                onServiceInstalled(event);
                break;

            case SERVICE_REMOVED:
                onServiceRemoved(event);
                break;

            case SERVICE_STARTED:
                onServiceStarted(event);
                break;

            case SERVICE_STOPPING:
                onServiceStopping(event);
                break;

            case SERVICE_STOPPED:
                onServiceStopped(event);
                break;

            default:
                // no-op
                break;
        }
    }

    private void onServiceStopping(ServiceEvent event)
    {
        final ServiceController controller = event.getController();
        stoppingServices.add(controller);

        // resolve group name
        final ServiceName<?> groupName = controller.getGroupName();
        if (groupName != null)
        {
            final ServiceGroup serviceGroup = getOrCreateGroup(groupName);
            serviceGroup.removeService(controller);
        }

        // update injected references
        final Map<ServiceName<?>, ServiceGroupReference<?>> injectedReferences = controller.getInjectedReferences();
        injectedReferences.entrySet()
            .stream()
            .forEach((e) ->
            {
                final ServiceName<?> refGroupName = e.getKey();
                final ServiceGroupReference<?> reference = e.getValue();
                final ServiceGroup refGroup = getOrCreateGroup(refGroupName);
                refGroup.removeReference(reference);
            });

        final List<ServiceController> dependents = this.dependentServices.get(controller);
        for (ServiceController dependentService : dependents)
        {
            dependentService.getChannel()
                .add(new ServiceEvent(ServiceEventType.DEPENDENCIES_UNAVAILABLE, dependentService));
        }

        if (dependents.isEmpty())
        {
            controller.getChannel()
                .add(new ServiceEvent(ServiceEventType.DEPENDENTS_STOPPED, controller));
        }

    }

    private void onServiceStopped(ServiceEvent event)
    {
        final ServiceController controller = event.getController();
        startedServices.remove(controller);
        stoppingServices.remove(controller);

        for (ServiceController dependentService : stoppingServices)
        {
            if (startedServices.contains(dependentService))
            {
                final List<ServiceController> deps = resolvedDependencies.get(dependentService);
                boolean allStopped = true;
                for (int i = 0; i < deps.size() && allStopped; i++)
                {
                    allStopped &= !startedServices.contains(deps.get(i));
                }

                if (allStopped)
                {
                    dependentService.getChannel()
                        .add(new ServiceEvent(ServiceEventType.DEPENDENTS_STOPPED, dependentService));
                }
            }
        }
    }

    private void onServiceStarted(ServiceEvent event)
    {
        final ServiceController controller = event.getController();
        startedServices.add(controller);

        // update dependent services
        final List<ServiceController> dependentServices = this.dependentServices.get(controller);
        for (ServiceController dependentService : dependentServices)
        {
            checkDependenciesAvailable(dependentService);
        }

        // resolve group name
        final ServiceName<?> groupName = controller.getGroupName();
        if (groupName != null)
        {
            final ServiceGroup serviceGroup = getOrCreateGroup(groupName);
            serviceGroup.addService(controller);
        }

        // update injected references
        final Map<ServiceName<?>, ServiceGroupReference<?>> injectedReferences = controller.getInjectedReferences();
        injectedReferences.entrySet()
            .stream()
            .forEach((e) ->
            {
                final ServiceName<?> refGroupName = e.getKey();
                final ServiceGroupReference<?> reference = e.getValue();
                final ServiceGroup refGroup = getOrCreateGroup(refGroupName);
                refGroup.addReference(new ServiceGroupReferenceImpl(controller, reference, refGroup));
            });
    }

    private ServiceGroup getOrCreateGroup(final ServiceName<?> groupName)
    {
        ServiceGroup serviceGroup = groups.get(groupName);
        if (serviceGroup == null)
        {
            serviceGroup = new ServiceGroup(groupName);
            groups.put(groupName, serviceGroup);
        }
        return serviceGroup;
    }

    private void onServiceRemoved(ServiceEvent event)
    {
        final ServiceController controller = event.getController();

        installedServices.remove(controller.getServiceName());
        resolvedDependencies.remove(controller);

        final List<ServiceController> dependents = dependentServices.remove(controller);
        for (ServiceController serviceController : dependents)
        {
            final List<ServiceController> list = dependentServices.get(serviceController);
            if (list != null)
            {
                list.remove(controller);
            }
        }
        unresolvedDependencies.put(controller.getServiceName(), dependents);
    }

    private void onServiceInstalled(ServiceEvent event)
    {
        final ServiceController controller = event.getController();
        installedServices.put(controller.getServiceName(), controller);

        /** try to resolve this servie's dependencies */
        final Set<ServiceName<?>> dependencies = controller.getDependencies();
        final List<ServiceController> resolvedDependencies = new ArrayList<>();
        for (ServiceName<?> serviceName : dependencies)
        {
            final ServiceController resolvedController = installedServices.get(serviceName);

            if (resolvedController != null)
            {
                resolvedDependencies.add(resolvedController);
                dependentServices.get(resolvedController)
                    .add(controller);
            }
            else
            {
                List<ServiceController> list = unresolvedDependencies.get(serviceName);
                if (list == null)
                {
                    list = new ArrayList<>();
                    unresolvedDependencies.put(serviceName, list);
                }

                list.add(controller);
            }
        }
        this.resolvedDependencies.put(controller, resolvedDependencies);

        /** resolve other services' dependencies which depend on this service */
        List<ServiceController> dependents = unresolvedDependencies.remove(controller.getServiceName());
        if (dependents != null)
        {
            for (ServiceController dependent : dependents)
            {
                this.resolvedDependencies.get(dependent)
                    .add(controller);
            }
        }
        else
        {
            dependents = new ArrayList<>();
        }

        this.dependentServices.put(controller, dependents);
        checkDependenciesAvailable(controller);
    }

    private void checkDependenciesAvailable(ServiceController controller)
    {
        final Set<ServiceName<?>> dependencies = controller.getDependencies();
        final List<ServiceController> resolvedDependencies = this.resolvedDependencies.get(controller);

        boolean dependenciesAvailable = true;

        if (resolvedDependencies.size() == dependencies.size())
        {
            for (int i = 0; i < resolvedDependencies.size() && dependenciesAvailable; i++)
            {
                dependenciesAvailable &= startedServices.contains(resolvedDependencies.get(i));
                dependenciesAvailable &= !stoppingServices.contains(resolvedDependencies.get(i));
            }
        }
        else
        {
            dependenciesAvailable = false;
        }

        if (dependenciesAvailable)
        {
            controller.getChannel()
                .add(new ServiceEvent(ServiceEventType.DEPENDENCIES_AVAILABLE, controller, new ArrayList<>(resolvedDependencies)));
        }
    }

    final ServiceController getService(ServiceName<?> name)
    {
        return installedServices.get(name);
    }

    public Collection<ServiceController> getControllers()
    {
        return installedServices.values();
    }

}
