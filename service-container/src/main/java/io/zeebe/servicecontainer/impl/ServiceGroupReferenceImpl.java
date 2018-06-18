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

import io.zeebe.servicecontainer.ServiceGroupReference;
import io.zeebe.servicecontainer.ServiceName;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

@SuppressWarnings("rawtypes")
public class ServiceGroupReferenceImpl {
  protected final ServiceController referringService;
  protected final ServiceGroupReference injector;
  protected final ServiceGroup group;
  protected final Map<ServiceName, Object> injectedValues = new HashMap<>();

  public ServiceGroupReferenceImpl(
      ServiceController referringService, ServiceGroupReference injector, ServiceGroup group) {
    this.referringService = referringService;
    this.injector = injector;
    this.group = group;
  }

  public ServiceController getReferringService() {
    return referringService;
  }

  public ServiceGroupReference getInjector() {
    return injector;
  }

  public ServiceGroup getGroup() {
    return group;
  }

  public void addValue(ServiceName name, Object value) {
    referringService.addReferencedValue(injector, name, value);
    injectedValues.put(name, value);
  }

  public void removeValue(ServiceName name, Object value) {
    if (injectedValues.containsKey(name)) {
      referringService.removeReferencedValue(injector, name, value);
      injectedValues.remove(name);
    }
  }

  public void uninject() {
    final Set<Entry<ServiceName, Object>> entries = injectedValues.entrySet();
    for (Entry<ServiceName, Object> e : entries) {
      if (injectedValues.containsKey(e.getKey())) {
        referringService.removeReferencedValue(injector, e.getKey(), e.getValue());
      }
    }
    injectedValues.clear();
  }
}
