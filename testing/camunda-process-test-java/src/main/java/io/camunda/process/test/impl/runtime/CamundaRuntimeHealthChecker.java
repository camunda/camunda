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
package io.camunda.process.test.impl.runtime;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.PartitionBrokerHealth;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

/**
 * Checks the health of a Camunda cluster by querying the topology endpoint, verifying that all
 * partitions are healthy, and probing that the broker processes commands.
 */
public class CamundaRuntimeHealthChecker {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

  private static final int HTTP_BAD_REQUEST = 400;

  private static final byte[] PROBE_RESOURCE = "not a BPMN model".getBytes(StandardCharsets.UTF_8);

  /**
   * Waits until the cluster is ready (all partitions healthy, at least one partition available)
   * using a default timeout of 10 seconds. Throws a {@link RuntimeException} if the cluster does
   * not become ready within the timeout.
   *
   * @param camundaClientBuilderFactory factory used to create a short-lived {@link CamundaClient}
   * @return the cluster {@link Topology} once ready
   */
  public static Topology waitUntilClusterReady(
      final CamundaClientBuilderFactory camundaClientBuilderFactory) {
    return waitUntilClusterReady(camundaClientBuilderFactory, DEFAULT_TIMEOUT);
  }

  /**
   * Waits until the cluster is ready (all partitions healthy, at least one partition available).
   * Throws a {@link RuntimeException} if the cluster does not become ready within the timeout.
   *
   * @param camundaClientBuilderFactory factory used to create a short-lived {@link CamundaClient}
   * @param timeout maximum time to wait
   * @return the cluster {@link Topology} once ready
   */
  public static Topology waitUntilClusterReady(
      final CamundaClientBuilderFactory camundaClientBuilderFactory, final Duration timeout) {
    final AtomicReference<Topology> topologyRef = new AtomicReference<>();
    try {
      Awaitility.await("Wait for cluster to be ready")
          .atMost(timeout)
          .pollInterval(Duration.ofSeconds(1))
          .pollDelay(Duration.ZERO)
          .ignoreExceptions()
          .untilAsserted(() -> topologyRef.set(checkClusterHealth(camundaClientBuilderFactory)));
    } catch (final ConditionTimeoutException e) {
      throw new RuntimeException(
          Optional.ofNullable(e.getCause())
              .map(Throwable::getMessage)
              .orElse(
                  String.format(
                      "Cluster did not become ready within %s seconds.", timeout.getSeconds())),
          e);
    }
    return topologyRef.get();
  }

  private static Topology checkClusterHealth(
      final CamundaClientBuilderFactory camundaClientBuilderFactory) {
    try (final CamundaClient camundaClient = camundaClientBuilderFactory.get().build()) {
      final Topology topology = camundaClient.newTopologyRequest().send().join();

      final boolean hasAtLeastOnePartition =
          topology.getBrokers().stream()
              .anyMatch(brokerInfo -> !brokerInfo.getPartitions().isEmpty());

      final boolean isHealthy =
          topology.getBrokers().stream()
              .flatMap(brokerInfo -> brokerInfo.getPartitions().stream())
              .map(PartitionInfo::getHealth)
              .allMatch(PartitionBrokerHealth.HEALTHY::equals);

      if (!hasAtLeastOnePartition) {
        throw new IllegalStateException(
            String.format(
                "Cluster has no available partitions. Please check the runtime logs for errors. [topology: %s]",
                topology));
      }
      if (!isHealthy) {
        throw new IllegalStateException(
            String.format("Cluster is unhealthy. [topology: %s]", topology));
      }
      probeCommandApi(camundaClient);
      return topology;
    }
  }

  /**
   * Deploys a resource that is not a valid BPMN model. The engine can only reject a resource once
   * the partition processes commands, so a client error proves that deployments are accepted. This
   * is stricter than the topology, which can report healthy partitions while the command API
   * handler of the partition leader is not registered yet (HTTP 500). The rejected deployment
   * changes no engine state.
   */
  private static void probeCommandApi(final CamundaClient camundaClient) {
    try {
      camundaClient
          .newDeployResourceCommand()
          .useRest()
          .addResourceBytes(PROBE_RESOURCE, "camunda-process-test-readiness-probe.bpmn")
          .send()
          .join();
      throw new IllegalStateException(
          "Expected the engine to reject the readiness probe resource, but it was accepted.");
    } catch (final ProblemException e) {
      if (e.code() != HTTP_BAD_REQUEST) {
        throw e;
      }
    }
  }
}
