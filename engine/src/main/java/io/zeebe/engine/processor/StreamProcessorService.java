/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor;

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
