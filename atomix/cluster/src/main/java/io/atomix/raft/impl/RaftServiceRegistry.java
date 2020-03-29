/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft.impl;

import io.atomix.raft.service.RaftServiceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Raft service registry. */
public class RaftServiceRegistry implements Iterable<RaftServiceContext> {

  private final Map<String, RaftServiceContext> services = new ConcurrentHashMap<>();

  /**
   * Registers a new service.
   *
   * @param service the service to register
   */
  public void registerService(final RaftServiceContext service) {
    services.put(service.serviceName(), service);
  }

  /**
   * Unregisters the given service.
   *
   * @param service the service to unregister
   */
  public void unregisterService(final RaftServiceContext service) {
    services.remove(service.serviceName());
  }

  /**
   * Gets a registered service by name.
   *
   * @param name the service name
   * @return the registered service
   */
  public RaftServiceContext getService(final String name) {
    return services.get(name);
  }

  @Override
  public Iterator<RaftServiceContext> iterator() {
    return services.values().iterator();
  }

  /**
   * Returns a copy of the services registered in the registry.
   *
   * @return a copy of the registered services
   */
  public Collection<RaftServiceContext> copyValues() {
    return new ArrayList<>(services.values());
  }
}
