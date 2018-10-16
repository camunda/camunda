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
package io.zeebe.logstreams.impl.service;

import io.zeebe.logstreams.processor.StreamProcessorController;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import io.zeebe.util.sched.future.ActorFuture;

public class StreamProcessorService implements Service<StreamProcessorService> {
  private final StreamProcessorController controller;
  private final ServiceContainer serviceContainer;
  private final ServiceName<StreamProcessorService> serviceName;

  public StreamProcessorService(
      StreamProcessorController controller,
      ServiceContainer serviceContainer,
      ServiceName<StreamProcessorService> serviceName) {
    this.controller = controller;
    this.serviceContainer = serviceContainer;
    this.serviceName = serviceName;
  }

  @Override
  public void start(ServiceStartContext startContext) {
    startContext.async(controller.openAsync());
  }

  @Override
  public void stop(ServiceStopContext stopContext) {
    stopContext.async(controller.closeAsync());
  }

  @Override
  public StreamProcessorService get() {
    return this;
  }

  public ActorFuture<Void> closeAsync() {
    return serviceContainer.removeService(serviceName);
  }

  public void close() {
    closeAsync().join();
  }

  public StreamProcessorController getController() {
    return controller;
  }
}
