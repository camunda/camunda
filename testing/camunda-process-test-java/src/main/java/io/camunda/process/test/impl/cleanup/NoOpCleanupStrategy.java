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
import io.camunda.process.test.impl.client.CamundaManagementClient;
import java.time.Instant;
import java.util.Set;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NoOpCleanupStrategy implements CleanupStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(NoOpCleanupStrategy.class);

  @Override
  public void cleanup(
      final CamundaManagementClient managementClient,
      final Supplier<CamundaClient> clientSupplier,
      final Instant testCaseStartTime,
      final Set<Long> deploymentKeys) {
    LOG.debug("Runtime data deletion mode is NONE. Skipping.");
  }
}
