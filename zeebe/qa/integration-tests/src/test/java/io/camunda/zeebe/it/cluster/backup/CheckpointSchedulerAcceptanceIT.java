/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.configuration.Filesystem;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.asserts.TopologyAssert;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.assertj.core.api.AssertionsForClassTypes;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@ZeebeIntegration
public class CheckpointSchedulerAcceptanceIT {
  private static @TempDir Path tempDir;
  private static final int PARTITION_COUNT = 3;
  private final Path basePath = tempDir.resolve(UUID.randomUUID().toString());
  private CamundaClient client;

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(3)
          .withGatewaysCount(1)
          .withReplicationFactor(3)
          .withPartitionsCount(PARTITION_COUNT)
          .withEmbeddedGateway(false)
          .withGatewayConfig(
              g -> {
                final var membership = g.unifiedConfig().getCluster().getMembership();
                // Reduce timeouts so that the test is faster
                membership.setProbeInterval(Duration.ofMillis(100));
                membership.setFailureTimeout(Duration.ofSeconds(2));
              })
          .build();

  private void defaultBackupConfig(final TestStandaloneBroker broker) {
    broker.withUnifiedConfig(
        cfg -> {
          cfg.getData()
              .getPrimaryStorage()
              .getBackup()
              .setStore(PrimaryStorageBackup.BackupStoreType.FILESYSTEM);

          final var config = new Filesystem();
          config.setBasePath(basePath.toAbsolutePath().toString());
          cfg.getData().getPrimaryStorage().getBackup().setFilesystem(config);
          cfg.getData().getPrimaryStorage().getBackup().setContinuous(true);
        });
  }

  private void configureBroker(
      final TestStandaloneBroker broker, final Consumer<PrimaryStorageBackup> modifier) {
    defaultBackupConfig(broker);
    broker.withUnifiedConfig(cfg -> modifier.accept(cfg.getData().getPrimaryStorage().getBackup()));
  }

  @Test
  void shouldCreateBackupsAccordingToSchedule() {
    cluster
        .brokers()
        .values()
        .forEach(broker -> configureBroker(broker, backupCfg -> backupCfg.setSchedule("PT5S")));
    cluster.start().awaitCompleteTopology();

    final AtomicLong backupId = new AtomicLong(-1);
    final var actuator = BackupActuator.of(cluster.availableGateway());
    client = cluster.newClientBuilder().build();

    for (int i = 0; i < 2; i++) {
      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                final var state = actuator.state();

                assertBackupsCreated(state);
                assertCheckpointsCreated(state, CheckpointType.SCHEDULED_BACKUP);

                final var checkpointIds =
                    state.getCheckpointStates().stream()
                        .map(PartitionCheckpointState::checkpointId)
                        .collect(Collectors.toSet())
                        .stream()
                        .toList();

                final var backupIds =
                    state.getBackupStates().stream()
                        .map(PartitionCheckpointState::checkpointId)
                        .collect(Collectors.toSet())
                        .stream()
                        .toList();

                assertThat(backupIds.size()).isEqualTo(checkpointIds.size()).isOne();
                assertThat(backupIds.getFirst()).isEqualTo(checkpointIds.getFirst());

                if (backupId.get() != -1) {
                  AssertionsForClassTypes.assertThat(backupIds.getFirst())
                      .isGreaterThan(backupId.get());
                }
                backupId.set(backupIds.getFirst());
              });
    }
  }

  @Test
  void shouldCreateCheckpointsAccordingToSchedule() {
    cluster
        .brokers()
        .values()
        .forEach(
            broker ->
                configureBroker(
                    broker, backupCfg -> backupCfg.setCheckpointInterval(Duration.ofSeconds(5))));
    cluster.start().awaitCompleteTopology();

    final AtomicLong checkpointId = new AtomicLong(-1);
    final var actuator = BackupActuator.of(cluster.availableGateway());

    for (int i = 0; i < 2; i++) {
      Awaitility.await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                final var state = actuator.state();

                assertCheckpointsCreated(state, CheckpointType.MARKER);

                final var checkpointIds =
                    state.getCheckpointStates().stream()
                        .map(PartitionCheckpointState::checkpointId)
                        .collect(Collectors.toSet())
                        .stream()
                        .toList();

                if (checkpointId.get() != -1) {
                  AssertionsForClassTypes.assertThat(checkpointIds.getFirst())
                      .isGreaterThan(checkpointId.get());
                }
                checkpointId.set(checkpointIds.getFirst());
              });
    }
  }

  @Test
  void backupSchedulingShouldBeHandedOver() {
    cluster
        .brokers()
        .values()
        .forEach(
            broker ->
                configureBroker(
                    broker, backupCfg -> backupCfg.setCheckpointInterval(Duration.ofSeconds(5))));

    cluster.start().awaitCompleteTopology();
    client = cluster.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(2)).build();
    final AtomicLong checkpointId = new AtomicLong(-1);

    Awaitility.await("initial checkpoint is created from broker 0")
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              final var actuator = BackupActuator.of(cluster.availableGateway());
              final var state = actuator.state();

              assertCheckpointsCreated(state, CheckpointType.MARKER);

              final var checkpointIds =
                  state.getCheckpointStates().stream()
                      .map(PartitionCheckpointState::checkpointId)
                      .collect(Collectors.toSet())
                      .stream()
                      .toList();

              if (checkpointId.get() != -1) {
                AssertionsForClassTypes.assertThat(checkpointIds.getFirst())
                    .isGreaterThan(checkpointId.get());
              }
              checkpointId.set(checkpointIds.getFirst());
            });

    cluster.brokers().get(MemberId.from("0")).stop();
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var topology = client.newTopologyRequest().send().join();
              TopologyAssert.assertThat(topology)
                  .describedAs("Broker 0 is removed from topology")
                  .doesNotContainBroker(0);
              TopologyAssert.assertThat(topology).hasLeaderForEachPartition(PARTITION_COUNT);
            });

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              final var actuator = BackupActuator.of(cluster.availableGateway());
              final var state = actuator.state();

              assertCheckpointsCreated(state, CheckpointType.MARKER);

              final var checkpointIds =
                  state.getCheckpointStates().stream()
                      .map(PartitionCheckpointState::checkpointId)
                      .collect(Collectors.toSet())
                      .stream()
                      .toList();

              if (checkpointId.get() != -1) {
                assertThat(checkpointIds.getFirst()).isGreaterThan(checkpointId.get());
              }
              checkpointId.set(checkpointIds.getFirst());
            });
  }

  @Test
  void checkpointSchedulingShouldBeHandedOver() {
    cluster.brokers().values().forEach(broker -> configureBroker(broker, backupCfg -> {}));

    cluster.start().awaitCompleteTopology();
    client = cluster.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(2)).build();

    cluster.brokers().get(MemberId.from("0")).stop();
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var topology = client.newTopologyRequest().send().join();
              TopologyAssert.assertThat(topology)
                  .describedAs("Broker 0 is removed from topology", PARTITION_COUNT)
                  .doesNotContainBroker(0);
            });

    // Start broker 0 with checkpoint interval configured
    final var broker0 = cluster.brokers().get(MemberId.from("0"));
    configureBroker(broker0, backupCfg -> backupCfg.setCheckpointInterval(Duration.ofSeconds(5)));
    broker0.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var topology = client.newTopologyRequest().send().join();
              TopologyAssert.assertThat(topology)
                  .describedAs("Broker 0 is part of the topology", PARTITION_COUNT)
                  .containsBroker(0);
              TopologyAssert.assertThat(topology).hasLeaderForEachPartition(PARTITION_COUNT);
            });

    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              final var actuator = BackupActuator.of(cluster.availableGateway());
              final var state = actuator.state();

              assertCheckpointsCreated(state, CheckpointType.MARKER);
            });
  }

  @Test
  void shouldNotStartSchedulingIfNotLowest() {
    cluster.brokers().values().forEach(broker -> configureBroker(broker, backupCfg -> {}));

    cluster.start().awaitCompleteTopology();
    client = cluster.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(2)).build();

    cluster.brokers().get(MemberId.from("1")).stop();
    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var topology = client.newTopologyRequest().send().join();
              TopologyAssert.assertThat(topology)
                  .describedAs("Broker 1 is removed from topology", PARTITION_COUNT)
                  .doesNotContainBroker(1);
            });

    // Start broker 1 with checkpoint interval configured
    final var broker1 = cluster.brokers().get(MemberId.from("1"));
    configureBroker(broker1, backupCfg -> backupCfg.setCheckpointInterval(Duration.ofSeconds(5)));
    broker1.start();

    Awaitility.await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var topology = client.newTopologyRequest().send().join();
              TopologyAssert.assertThat(topology)
                  .describedAs("Broker 1 is part of the topology", PARTITION_COUNT)
                  .containsBroker(1);
              TopologyAssert.assertThat(topology).hasLeaderForEachPartition(PARTITION_COUNT);
            });

    Awaitility.await()
        .during(Duration.ofSeconds(5))
        .atMost(Duration.ofSeconds(15))
        .untilAsserted(
            () -> {
              final var actuator = BackupActuator.of(cluster.availableGateway());
              final var state = actuator.state();
              assertThat(state.getCheckpointStates()).isEmpty();
              assertThat(state.getBackupStates()).isEmpty();
            });
  }

  private void assertCheckpointsCreated(
      final CheckpointStateResponse response, final CheckpointType checkpointType) {
    assertThat(response.getCheckpointStates()).hasSize(PARTITION_COUNT);
    assertThat(
            response.getCheckpointStates().stream()
                .allMatch(pState -> pState.checkpointType() == checkpointType))
        .isTrue();
  }

  private void assertBackupsCreated(final CheckpointStateResponse response) {
    assertThat(response.getBackupStates()).hasSize(PARTITION_COUNT);
    assertThat(
            response.getBackupStates().stream()
                .allMatch(pState -> pState.checkpointType() == CheckpointType.SCHEDULED_BACKUP))
        .isTrue();
  }
}
