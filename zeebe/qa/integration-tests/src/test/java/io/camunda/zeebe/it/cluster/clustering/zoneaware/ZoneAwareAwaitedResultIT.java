/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering.zoneaware;

import static io.camunda.zeebe.qa.util.cluster.util.ZoneFixtures.ZONE_A;
import static io.camunda.zeebe.qa.util.cluster.util.ZoneFixtures.ZONE_B;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.configuration.cluster.Zone;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.RebalanceActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

/**
 * Node indexes repeat across zones ({@code zone-a_0} and {@code zone-b_0}), so the broker that
 * receives an awaited request can't be found from the request id alone. Zone B is preferred at
 * first, so its one broker receives the requests, and swapping the priorities moves leadership to
 * zone A.
 */
@ZeebeIntegration
final class ZoneAwareAwaitedResultIT {

  private static final int PARTITIONS_COUNT = 3;
  private static final String TENANT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final MemberId ZONE_B_BROKER = MemberId.from(ZONE_B, 0);

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withName("zone-aware-awaited-result")
          .withEmbeddedGateway(true)
          .withBrokersCount(3)
          .withPartitionsCount(PARTITIONS_COUNT)
          .withReplicationFactor(3)
          .multiZone(List.of(new Zone(ZONE_A, 2, 2, 10), new Zone(ZONE_B, 1, 1, 100)))
          .build();

  @AutoClose private CamundaClient client;

  @Test
  void shouldRespondToAwaitedResultsAfterLeadershipMovedToAnotherZone() {
    // given
    ZoneHelpers.assertLeadersInZone(cluster, TENANT, ZONE_B, PARTITIONS_COUNT);
    client = cluster.brokers().get(ZONE_B_BROKER).newClientBuilder().build();
    final var processId = "zone-await-result";
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(processId))
                .endEvent()
                .done(),
            "zone-await-result.bpmn")
        .send()
        .join();
    final List<CamundaFuture<ProcessInstanceResult>> results = new ArrayList<>();
    final Map<Long, ActivatedJob> jobs = new HashMap<>();
    Awaitility.await("an instance awaiting its result on every partition")
        .atMost(Duration.ofSeconds(30))
        .until(
            () -> {
              results.add(
                  client
                      .newCreateInstanceCommand()
                      .bpmnProcessId(processId)
                      .latestVersion()
                      .withResult()
                      .requestTimeout(Duration.ofMinutes(2))
                      .send());
              activateJobs(processId, jobs);
              return jobs.keySet().stream().map(Protocol::decodePartitionId).distinct().count()
                  == PARTITIONS_COUNT;
            });
    Awaitility.await("a job for every awaited instance")
        .atMost(Duration.ofSeconds(30))
        .until(() -> activateJobs(processId, jobs) == results.size());

    // when
    final var actuator = ClusterActuator.of(cluster.availableGateway());
    final var priorityChange = actuator.updateZonePriorities(List.of(ZONE_A, ZONE_B), false);
    Awaitility.await("zone A is preferred")
        .untilAsserted(
            () -> ClusterActuatorAssert.assertThat(actuator).hasAppliedChanges(priorityChange));
    ZoneHelpers.awaitLeadersInZoneAfterRebalance(
        cluster.brokers(),
        RebalanceActuator.of(cluster.availableGateway()),
        TENANT,
        ZONE_A,
        PARTITIONS_COUNT);
    jobs.values().forEach(job -> client.newCompleteCommand(job).send().join());

    // then
    assertThat(results)
        .allSatisfy(
            result ->
                assertThat(result.join(30, TimeUnit.SECONDS).getBpmnProcessId())
                    .isEqualTo(processId));
  }

  private int activateJobs(final String jobType, final Map<Long, ActivatedJob> jobs) {
    client
        .newActivateJobsCommand()
        .jobType(jobType)
        .maxJobsToActivate(10)
        .timeout(Duration.ofMinutes(5))
        .send()
        .join()
        .getJobs()
        .forEach(job -> jobs.put(job.getKey(), job));
    return jobs.size();
  }
}
