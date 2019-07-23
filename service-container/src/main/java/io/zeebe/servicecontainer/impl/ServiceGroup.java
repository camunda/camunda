/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.servicecontainer.impl;

import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ServiceGroup {
  protected final ServiceName<?> groupName;

  protected final List<ServiceController> controllers = new ArrayList<>();
  protected final List<ServiceGroupReferenceImpl> references = new ArrayList<>();

  public ServiceGroup(ServiceName<?> groupName) {
    this.groupName = groupName;
  }

  public void addReference(ServiceGroupReferenceImpl reference) {
    references.add(reference);
    onReferenceAdded(reference);
  }

  private void onReferenceAdded(ServiceGroupReferenceImpl reference) {
    for (ServiceController serviceController : controllers) {
      final Service<?> service = serviceController.getService();
      reference.addValue(serviceController.getServiceName(), service.get());
    }
  }

  public void removeReference(ServiceGroupReference<?> reference) {
    final Iterator<ServiceGroupReferenceImpl> iterator = references.iterator();

    // could be more efficient with further indexing
    while (iterator.hasNext()) {
      final ServiceGroupReferenceImpl serviceGroupReferenceImpl = iterator.next();
      if (serviceGroupReferenceImpl.injector == reference) {
        serviceGroupReferenceImpl.uninject();
        iterator.remove();
        break;
      }
    }
  }

  public void addService(ServiceController controller) {
    controllers.add(controller);
    onServiceAdded(controller);
  }

  public void removeService(ServiceController controller) {
    controllers.remove(controller);
    onServiceRemoved(controller);
  }

  private void onServiceAdded(ServiceController controller) {
    final Object serviceObject = controller.getService().get();

    for (int i = 0; i < references.size(); i++) {
      final ServiceGroupReferenceImpl reference = references.get(i);
      reference.addValue(controller.getServiceName(), serviceObject);
    }
  }

  private void onServiceRemoved(ServiceController controller) {
    final Object serviceObject = controller.getService().get();

    for (int i = 0; i < references.size(); i++) {
      final ServiceGroupReferenceImpl reference = references.get(i);
      reference.removeValue(controller.getServiceName(), serviceObject);
    }
  }

  public List<ServiceController> getControllers() {
    return controllers;
  }
}
