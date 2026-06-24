/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Filesystem;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.PartitionBackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that backup runtime metadata (ranges) are not accumulated when continuous backups are
 * disabled. This is the counterpart to {@link BackupRangeTrackingIT}, which tests that ranges ARE
 * tracked when continuous backups are enabled.
 */
@Testcontainers
@ZeebeIntegration
final class BackupMetadataTrackingDisabledIT {
  private static @TempDir Path tempDir;

  private final Path basePath = tempDir.resolve(UUID.randomUUID().toString());

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(2)
          .withGatewaysCount(1)
          .withReplicationFactor(1)
          .withPartitionsCount(2)
          .withEmbeddedGateway(false)
          .withBrokerConfig(this::configureBroker)
          .build();

  @Test
  void shouldNotAccumulateRangesWhenContinuousBackupsDisabled() {
    // given
    final var actuator = BackupActuator.of(cluster.availableGateway());
    try (final var client = cluster.newClientBuilder().build()) {
      client.newPublishMessageCommand().messageName("name").correlationKey("key").send().join();
    }

    // when -- take multiple backups
    actuator.take(1);
    waitUntilBackupIsCompleted(actuator, 1L);
    actuator.take(2);
    waitUntilBackupIsCompleted(actuator, 2L);

    // then -- no ranges should have been created
    assertThat(actuator.state().getRanges())
        .describedAs("Ranges should not be tracked when continuous backups are disabled")
        .isEmpty();
  }

  private void configureBroker(final TestStandaloneBroker broker) {
    broker.withUnifiedConfig(
        cfg -> {
          // Continuous backups are disabled by default; no need to set explicitly.
          cfg.getData()
              .getPrimaryStorage()
              .getBackup()
              .setStore(PrimaryStorageBackup.BackupStoreType.FILESYSTEM);

          final var config = new Filesystem();
          config.setBasePath(basePath.toAbsolutePath().toString());
          cfg.getData().getPrimaryStorage().getBackup().setFilesystem(config);
        });
  }

  private static void waitUntilBackupIsCompleted(
      final BackupActuator actuator, final long backupId) {
    Awaitility.await("until a backup exists with the id %d".formatted(backupId))
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var status = actuator.status(backupId);
              assertThat(status)
                  .extracting(BackupInfo::getBackupId, BackupInfo::getState)
                  .containsExactly(backupId, StateCode.COMPLETED);
              assertThat(status.getDetails())
                  .flatExtracting(PartitionBackupInfo::getPartitionId)
                  .containsExactlyInAnyOrder(1, 2);
            });
  }
}
