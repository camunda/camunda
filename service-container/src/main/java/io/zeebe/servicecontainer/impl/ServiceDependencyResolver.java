/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.servicecontainer.impl;

import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.impl.ServiceEvent.ServiceEventType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

/** Stream processor tracking the dependencies of services. */
public class ServiceDependencyResolver {
  public static final Logger LOG = Loggers.SERVICE_CONTAINER_LOGGER;

  /** all services installed into the container, indexed by name */
  private final Map<ServiceName<?>, ServiceController> installedServices = new HashMap<>();

  /** map of service names which have not been resolved yet */
  private final Map<ServiceName<?>, List<ServiceController>> unresolvedDependencies =
      new HashMap<>();

  /** map of services and their resolved dependencies */
  private final Map<ServiceController, List<ServiceController>> resolvedDependencies =
      new HashMap<>();

  /** map of services and the services which depend on them */
  private final Map<ServiceController, List<ServiceController>> dependentServices = new HashMap<>();

  /** set of services which have started */
  private final Set<ServiceController> startedServices = new HashSet<>();

  /** set of services which are currently stopping */
  private final Set<ServiceController> stoppingServices = new HashSet<>();

  /** map of service groups */
  private final Map<ServiceName<?>, ServiceGroup> groups = new HashMap<>();

  private final List<ServiceController> rootServices = new ArrayList<>();

  public void onServiceEvent(ServiceEvent event) {
    switch (event.getType()) {
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

  private void onServiceStopping(ServiceEvent event) {
    final ServiceController controller = event.getController();
    stoppingServices.add(controller);

    // resolve group name
    final ServiceName<?> groupName = controller.getGroupName();
    if (groupName != null) {
      final ServiceGroup serviceGroup = getOrCreateGroup(groupName);
      serviceGroup.removeService(controller);
    }

    // update injected references
    final Map<ServiceName<?>, ServiceGroupReference<?>> injectedReferences =
        controller.getInjectedReferences();
    injectedReferences
        .entrySet()
        .forEach(
            (e) -> {
              final ServiceName<?> refGroupName = e.getKey();
              final ServiceGroupReference<?> reference = e.getValue();
              final ServiceGroup refGroup = getOrCreateGroup(refGroupName);
              refGroup.removeReference(reference);
            });

    final List<ServiceController> dependents = this.dependentServices.get(controller);
    for (ServiceController dependentService : dependents) {
      dependentService.fireEvent(ServiceEventType.DEPENDENCIES_UNAVAILABLE);
    }

    if (dependents.isEmpty()) {
      controller.fireEvent(ServiceEventType.DEPENDENTS_STOPPED);
    }
  }

  private void onServiceStopped(ServiceEvent event) {
    final ServiceController controller = event.getController();

    startedServices.remove(controller);
    stoppingServices.remove(controller);
  }

  private void onServiceRemoved(ServiceEvent event) {
    final ServiceController controller = event.getController();

    final List<ServiceController> dependencies = resolvedDependencies.remove(controller);
    for (ServiceController dependency : dependencies) {
      final List<ServiceController> dependents = dependentServices.get(dependency);

      if (dependents != null) {
        dependents.remove(controller);
        if (stoppingServices.contains(dependency)) {
          final boolean allStopped = dependents.isEmpty();
          if (allStopped) {
            dependency.fireEvent(ServiceEventType.DEPENDENTS_STOPPED);
          }
        }
      }
    }

    installedServices.remove(controller.getServiceName());

    final List<ServiceController> dependents = dependentServices.remove(controller);
    assert dependents == null || dependents.isEmpty()
        : "Problem on dependency clean up, not closed dependents: "
            + dependents
            + " for controller "
            + controller.getServiceName();
  }

  private void onServiceStarted(ServiceEvent event) {
    final ServiceController controller = event.getController();
    startedServices.add(controller);

    // update dependent services
    final List<ServiceController> dependentServices = this.dependentServices.get(controller);
    for (ServiceController dependentService : dependentServices) {
      checkDependenciesAvailable(dependentService);
    }

    // resolve group name
    final ServiceName<?> groupName = controller.getGroupName();
    if (groupName != null) {
      final ServiceGroup serviceGroup = getOrCreateGroup(groupName);
      serviceGroup.addService(controller);
    }

    // update injected references
    final Map<ServiceName<?>, ServiceGroupReference<?>> injectedReferences =
        controller.getInjectedReferences();
    injectedReferences
        .entrySet()
        .forEach(
            (e) -> {
              final ServiceName<?> refGroupName = e.getKey();
              final ServiceGroupReference<?> reference = e.getValue();
              final ServiceGroup refGroup = getOrCreateGroup(refGroupName);
              refGroup.addReference(new ServiceGroupReferenceImpl(controller, reference, refGroup));
            });
  }

  private ServiceGroup getOrCreateGroup(final ServiceName<?> groupName) {
    ServiceGroup serviceGroup = groups.get(groupName);
    if (serviceGroup == null) {
      serviceGroup = new ServiceGroup(groupName);
      groups.put(groupName, serviceGroup);
    }
    return serviceGroup;
  }

  private void onServiceInstalled(ServiceEvent event) {
    final ServiceController controller = event.getController();
    installedServices.put(controller.getServiceName(), controller);

    /** try to resolve this service's dependencies */
    final Set<ServiceName<?>> dependencies = controller.getDependencies();
    final List<ServiceController> resolvedDependencies = new ArrayList<>();
    for (ServiceName<?> serviceName : dependencies) {
      final ServiceController resolvedController = installedServices.get(serviceName);

      if (resolvedController != null) {
        resolvedDependencies.add(resolvedController);
        dependentServices.get(resolvedController).add(controller);
      } else {
        List<ServiceController> list = unresolvedDependencies.get(serviceName);
        if (list == null) {
          list = new ArrayList<>();
          unresolvedDependencies.put(serviceName, list);
        }

        list.add(controller);
      }
    }
    this.resolvedDependencies.put(controller, resolvedDependencies);
    if (dependencies.isEmpty()) {
      rootServices.add(controller);
    }

    /** resolve other services' dependencies which depend on this service */
    List<ServiceController> dependents = unresolvedDependencies.remove(controller.getServiceName());
    if (dependents != null) {
      for (ServiceController dependent : dependents) {
        this.resolvedDependencies.get(dependent).add(controller);
      }
    } else {
      dependents = new ArrayList<>();
    }

    this.dependentServices.put(controller, dependents);
    checkDependenciesAvailable(controller);
  }

  public List<ServiceController> getRootServices() {
    return rootServices;
  }

  private void checkDependenciesAvailable(ServiceController controller) {
    final Set<ServiceName<?>> dependencies = controller.getDependencies();
    final List<ServiceController> resolvedDependencies = this.resolvedDependencies.get(controller);

    boolean dependenciesAvailable = true;

    if (resolvedDependencies.size() == dependencies.size()) {
      for (int i = 0; i < resolvedDependencies.size() && dependenciesAvailable; i++) {
        dependenciesAvailable &= startedServices.contains(resolvedDependencies.get(i));
        dependenciesAvailable &= !stoppingServices.contains(resolvedDependencies.get(i));
      }
    } else {
      dependenciesAvailable = false;
    }

    if (dependenciesAvailable) {
      controller.fireEvent(
          ServiceEventType.DEPENDENCIES_AVAILABLE, new ArrayList<>(resolvedDependencies));
    }
  }

  final ServiceController getService(ServiceName<?> name) {
    return installedServices.get(name);
  }

  public Collection<ServiceController> getControllers() {
    return installedServices.values();
  }
}
