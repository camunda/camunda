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
import io.camunda.client.api.command.ProblemException;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ClusterPurgeCleanupStrategy implements CleanupStrategy {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterPurgeCleanupStrategy.class);

  private static final Duration COMMAND_API_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration COMMAND_API_POLL_INTERVAL = Duration.ofMillis(100);

  private static final int HTTP_NOT_FOUND = 404;
  private static final int HTTP_SERVER_ERROR = 500;

  /**
   * Key of a job that never exists. The upper bits of a key hold the partition id, so this key
   * routes the command to the deployment partition (id 1); the lower bits are the key's sequence
   * number.
   */
  private static final long PROBE_JOB_KEY = (1L << 51) | 1;

  @Override
  public void cleanup(
      final CamundaManagementClient managementClient,
      final Supplier<CamundaClient> clientSupplier,
      final Instant testCaseStartTime) {
    LOG.debug("Purging cluster runtime data");
    final Instant startTime = Instant.now();

    managementClient.purgeCluster();
    try (final CamundaClient client = clientSupplier.get()) {
      waitUntilCommandApiAcceptsCommands(client);
    }

    final Duration duration = Duration.between(startTime, Instant.now());
    LOG.debug("Cluster runtime data purged in {}", duration);
  }

  /**
   * Waits until the deployment partition processes commands. Right after a purge the topology
   * reports the partition leader as healthy, while commands still fail with a server error because
   * the command API handler of the leader is not registered yet.
   *
   * <p>Completes a job that does not exist: only the broker can answer with 404, so it proves that
   * the command reached the partition. A server error means the partition is not ready yet.
   */
  private static void waitUntilCommandApiAcceptsCommands(final CamundaClient client) {
    Awaitility.await("Wait until the cluster processes commands after the purge")
        .atMost(COMMAND_API_TIMEOUT)
        .pollInterval(COMMAND_API_POLL_INTERVAL)
        .pollDelay(Duration.ZERO)
        .until(() -> isCommandApiReady(client));
  }

  private static boolean isCommandApiReady(final CamundaClient client) {
    try {
      client.newCompleteCommand(PROBE_JOB_KEY).useRest().send().join();
      throw new IllegalStateException(
          "Expected the broker to reject completing a job that does not exist, but it was accepted.");
    } catch (final ProblemException e) {
      if (e.code() == HTTP_NOT_FOUND) {
        return true;
      }
      if (e.code() >= HTTP_SERVER_ERROR) {
        return false;
      }
      throw e;
    }
  }
}
