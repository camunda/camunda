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
package io.camunda.process.test.impl.cleanup;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import java.time.Instant;
import java.util.Collection;
import java.util.function.Supplier;

/** Internal strategy contract for test cleanup behavior based on the configured deletion mode. */
public interface CleanupStrategy {

  /**
   * Executes test cleanup for data created since the provided test case start time.
   *
   * @param managementClient management API client
   * @param clientSupplier supplier to create a Camunda API client
   * @param testCaseStartTime start time of the current test case
   * @param deployments deployments recorded during the current test case
   */
  void cleanup(
      CamundaManagementClient managementClient,
      Supplier<CamundaClient> clientSupplier,
      Instant testCaseStartTime,
      Collection<DeploymentEvent> deployments);
}
