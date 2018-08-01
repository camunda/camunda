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

    while (iterator.hasNext()) // could be more efficient with further indexing
    {
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
