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

public class ServiceEvent {
  public enum ServiceEventType {
    /** fired by the service when it is installed into the container */
    SERVICE_INSTALLED,

    /** fired by the service when it has failed to stop */
    SERVICE_START_FAILED,

    /** fired by the service when it is started */
    SERVICE_STARTED,

    /** fired by the service when it is about to stop */
    SERVICE_STOPPING,

    /** fired by the service when it has stopped */
    SERVICE_STOPPED,

    /** fired by the service when it is removed from the container */
    SERVICE_REMOVED,

    /**
     * Fired by the container when all of a service's dependencies are available. If the service has
     * at least one dependency, it means that this dependency has started. If the service has no
     * dependencies, the event is fired immediately, after the service is installed.
     */
    DEPENDENCIES_AVAILABLE,

    /** Fired by the container when a service's dependencies are about to become unavailable */
    DEPENDENCIES_UNAVAILABLE,

    /** FIRED by the container when a service's dependencies have stopped */
    DEPENDENTS_STOPPED
  }

  private final ServiceEventType type;

  private final ServiceController controller;

  private final Object payload;

  public ServiceEvent(ServiceEventType type, ServiceController controller, Object payload) {
    this.type = type;
    this.controller = controller;
    this.payload = payload;
  }

  public ServiceEvent(ServiceEventType type, ServiceController controller) {
    this(type, controller, null);
  }

  public ServiceEventType getType() {
    return type;
  }

  public ServiceController getController() {
    return controller;
  }

  public Object getPayload() {
    return payload;
  }

  @Override
  public String toString() {
    return String.format("%s - %s", controller, type);
  }
}
