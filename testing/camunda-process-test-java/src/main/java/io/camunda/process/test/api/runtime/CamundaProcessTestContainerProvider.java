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

import org.testcontainers.containers.GenericContainer;

/**
 * Provides a container for the managed/shared Camunda process test runtime based on Testcontainers.
 * The container is added to the runtime and included in the runtime's lifecycle. The runtime starts
 * the container before executing any tests and stops it after all tests have been executed.
 */
public interface CamundaProcessTestContainerProvider {

  /**
   * Creates a container for the Camunda process test runtime. Use the provided context to retrieve
   * information about the runtime.
   *
   * @param context the context of the Camunda process test runtime, which can be used to retrieve
   *     information
   * @return the container to be included in the Camunda process test runtime
   */
  GenericContainer<?> createContainer(final CamundaProcessTestContainerContext context);
}
