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
