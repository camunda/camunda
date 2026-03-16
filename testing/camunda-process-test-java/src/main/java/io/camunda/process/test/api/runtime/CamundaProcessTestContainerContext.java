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
package io.camunda.process.test.api.runtime;

import java.util.Collection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;

/**
 * Context of the Camunda process test runtime for container providers. The context provides
 * information about the runtime, such as the network and the containers included in the runtime.
 */
public interface CamundaProcessTestContainerContext {

  /**
   * Returns the network used by the containers in the Camunda process test runtime.
   *
   * @return the network used by the containers
   */
  Network getNetwork();

  /**
   * Returns the collection of containers included in the Camunda process test runtime.
   *
   * @return the collection of containers included in the runtime
   */
  Collection<GenericContainer<?>> getContainers();
}
