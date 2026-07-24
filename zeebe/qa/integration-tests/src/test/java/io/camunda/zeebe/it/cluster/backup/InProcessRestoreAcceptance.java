/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.configuration.Camunda;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.management.backups.TakeBackupRuntimeResponse;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.ClusterActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.topology.ClusterActuatorAssert;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Acceptance test for restoring a partition in-place ("in-process restore") while a running cluster
 * is in {@code RECOVERING} mode, as opposed to {@link RestoreAcceptance} which restores via a
 * separate, standalone restore application.
 */
public interface InProcessRestoreAcceptance {

  int BROKERS_COUNT = 3;
  int PARTITIONS_COUNT = 3;
  long BACKUP_ID = 42;
  String JOB_TYPE = "in-process-restore-job";
  String PROCESS_ID = "in-process-restore-process";

  ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @Test
  default void shouldRestoreClusterInProcess() {
    try (final var cluster =
            TestCluster.builder()
                .withBrokersCount(BROKERS_COUNT)
                .withPartitionsCount(PARTITIONS_COUNT)
                .withReplicationFactor(BROKERS_COUNT)
                .withEmbeddedGateway(true)
                .withBrokerConfig(broker -> configureBackupStore(broker.unifiedConfig()))
                .build()
                .start()
                .awaitCompleteTopology();
        final var client = cluster.newClientBuilder().build()) {

      // given -- every partition has at least one process instance with a pending job
      final var processInstanceKeys = deployProcessAndCreateInstances(client);

      // and -- a completed backup, taken after a snapshot on every broker
      takeSnapshotOnAllBrokers(cluster);
      final var backupActuator = BackupActuator.of(cluster.availableGateway());
      takeBackup(backupActuator, BACKUP_ID);

      // when -- the cluster is put into RECOVERING mode
      final var clusterActuator = ClusterActuator.of(cluster.availableGateway());
      final var toRecovering = clusterActuator.updateMode("RECOVERING", false);
      Awaitility.await("cluster transitions to RECOVERING")
          .timeout(Duration.ofSeconds(60))
          .untilAsserted(
              () ->
                  ClusterActuatorAssert.assertThat(clusterActuator)
                      .hasCompletedChanges(toRecovering)
                      .doesNotHavePendingChanges());

      // and -- a restore is triggered over the cluster's REST endpoint while recovering
      final var changeId = triggerRestore(client, BACKUP_ID);

      // then -- the restore change plan completes
      Awaitility.await("restore change plan completes")
          .timeout(Duration.ofMinutes(2))
          .untilAsserted(
              () ->
                  ClusterActuatorAssert.assertThat(clusterActuator)
                      .hasCompletedChanges(changeId)
                      .doesNotHavePendingChanges());

      // and -- every partition is ACTIVE again on every broker
      Awaitility.await("every partition reports ACTIVE again")
          .timeout(Duration.ofSeconds(60))
          .untilAsserted(
              () -> {
                final var topology = clusterActuator.getTopology();
                assertThat(topology.getBrokers())
                    .flatExtracting(BrokerState::getPartitions)
                    .extracting(PartitionState::getState)
                    .allMatch(state -> state == PartitionStateCode.ACTIVE);
              });

      // and -- jobs from every partition are activated again, proving partition data was restored
      // and not just topology/mode
      assertJobsActivatedFromEveryPartition(client, processInstanceKeys.size());

      // and -- the cluster accepts new work again after the restore
      final var newInstance =
          client.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion().send().join();
      assertThat(newInstance.getProcessInstanceKey()).isPositive();
    }
  }

  private List<Long> deployProcessAndCreateInstances(final CamundaClient client) {
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    final var deploymentKey =
        client
            .newDeployResourceCommand()
            .addProcessModel(process, "process.bpmn")
            .send()
            .join()
            .getKey();
    new ZeebeResourcesHelper(client).waitUntilDeploymentIsDone(deploymentKey);

    final var totalInstances = 2 * PARTITIONS_COUNT;
    final List<Long> processInstanceKeys = new ArrayList<>();
    Awaitility.await("every partition has at least one process instance with a pending job")
        .timeout(Duration.ofSeconds(60))
        // might throw exception when a partition has not yet received deployment distribution
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              while (processInstanceKeys.size() < totalInstances) {
                final var result =
                    client
                        .newCreateInstanceCommand()
                        .bpmnProcessId(PROCESS_ID)
                        .latestVersion()
                        .send()
                        .join();
                processInstanceKeys.add(result.getProcessInstanceKey());
              }

              final var partitionsWithInstance =
                  processInstanceKeys.stream()
                      .map(Protocol::decodePartitionId)
                      .collect(Collectors.toSet());
              assertThat(partitionsWithInstance)
                  .describedAs(
                      "every partition has at least one process instance with a pending job")
                  .containsExactlyInAnyOrderElementsOf(
                      IntStream.rangeClosed(1, PARTITIONS_COUNT).boxed().toList());
            });

    return processInstanceKeys;
  }

  private void takeSnapshotOnAllBrokers(final TestCluster cluster) {
    cluster
        .brokers()
        .values()
        .forEach(
            broker -> {
              final var partitions = PartitionsActuator.of(broker);
              partitions.takeSnapshot();
              Awaitility.await("snapshot is taken on broker " + broker.nodeId())
                  .atMost(Duration.ofSeconds(60))
                  .untilAsserted(
                      () ->
                          assertThat(partitions.query().values())
                              .allSatisfy(status -> assertThat(status.snapshotId()).isNotNull()));
            });
  }

  private void takeBackup(final BackupActuator actuator, final long backupId) {
    assertThat(actuator.take(backupId)).isInstanceOf(TakeBackupRuntimeResponse.class);
    Awaitility.await("until a backup exists with the given ID")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // 404 NOT_FOUND throws exception
        .untilAsserted(
            () -> {
              final var status = actuator.status(backupId);
              assertThat(status)
                  .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                  .containsExactly(backupId, StateCode.COMPLETED);
            });
  }

  private long triggerRestore(final CamundaClient client, final long backupId) {
    try {
      final var uri =
          URI.create(
              "%sv2/restore?dryRun=false".formatted(client.getConfiguration().getRestAddress()));
      final var body = OBJECT_MAPPER.writeValueAsString(Map.of("backupIds", List.of(backupId)));
      final var request =
          HttpRequest.newBuilder(uri)
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      final var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode())
          .describedAs("restore REST response: %s".formatted(response.body()))
          .isEqualTo(202);
      return OBJECT_MAPPER.readTree(response.body()).get("changeId").asLong();
    } catch (final IOException | InterruptedException e) {
      throw new RuntimeException("Failed to trigger restore via REST endpoint", e);
    }
  }

  private void assertJobsActivatedFromEveryPartition(
      final CamundaClient client, final int expectedJobCount) {
    final Set<ActivatedJob> activatedJobs = new HashSet<>();
    Awaitility.await("jobs from every partition are activated after restore")
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var jobs =
                  client
                      .newActivateJobsCommand()
                      .jobType(JOB_TYPE)
                      .maxJobsToActivate(expectedJobCount)
                      .send()
                      .join();
              activatedJobs.addAll(jobs.getJobs());

              final var partitionsWithActivatedJob =
                  activatedJobs.stream()
                      .map(job -> Protocol.decodePartitionId(job.getKey()))
                      .collect(Collectors.toSet());
              assertThat(partitionsWithActivatedJob)
                  .describedAs(
                      "jobs are activated from every partition, proving partition data was"
                          + " actually restored")
                  .containsExactlyInAnyOrderElementsOf(
                      IntStream.rangeClosed(1, PARTITIONS_COUNT).boxed().toList());
            });
  }

  /** Backup store can only be configured via UnifiedConfiguration */
  void configureBackupStore(final Camunda cfg);
}
