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
package io.zeebe.servicecontainer;

import io.zeebe.servicecontainer.impl.ServiceContainerImpl;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ServiceBuilder<S> {
  protected final ServiceContainerImpl serviceContainer;

  protected final ServiceName<S> name;
  protected final Service<S> service;
  protected ServiceName<?> groupName;

  protected Set<ServiceName<?>> dependencies = new HashSet<>();
  protected Map<ServiceName<?>, Collection<Injector<?>>> injectedDependencies = new HashMap<>();
  protected Map<ServiceName<?>, ServiceGroupReference<?>> injectedReferences = new HashMap<>();

  public ServiceBuilder(
      ServiceName<S> name, Service<S> service, ServiceContainerImpl serviceContainer) {
    this.name = name;
    this.service = service;
    this.serviceContainer = serviceContainer;
  }

  public ServiceBuilder<S> group(ServiceName<?> groupName) {
    this.groupName = groupName;
    return this;
  }

  public <T> ServiceBuilder<S> dependency(ServiceName<T> serviceName) {
    dependencies.add(serviceName);
    return this;
  }

  public <T> ServiceBuilder<S> dependency(
      ServiceName<? extends T> serviceName, Injector<T> injector) {
    Collection<Injector<?>> injectors = injectedDependencies.get(serviceName);
    if (injectors == null) {
      injectors = new ArrayList<>();
    }
    injectors.add(injector);

    injectedDependencies.put(serviceName, injectors);
    return dependency(serviceName);
  }

  public <T> ServiceBuilder<S> dependency(String name, Class<T> type) {
    return dependency(ServiceName.newServiceName(name, type));
  }

  public <T> ServiceBuilder<S> groupReference(
      ServiceName<T> groupName, ServiceGroupReference<T> injector) {
    injectedReferences.put(groupName, injector);
    return this;
  }

  public ActorFuture<S> install() {
    return serviceContainer.onServiceBuilt(this);
  }

  public ServiceName<S> getName() {
    return name;
  }

  public Service<S> getService() {
    return service;
  }

  public Set<ServiceName<?>> getDependencies() {
    return dependencies;
  }

  public Map<ServiceName<?>, Collection<Injector<?>>> getInjectedDependencies() {
    return injectedDependencies;
  }

  public Map<ServiceName<?>, ServiceGroupReference<?>> getInjectedReferences() {
    return injectedReferences;
  }

  public ServiceName<?> getGroupName() {
    return groupName;
  }
}
