/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.management.backups.TakeBackupRuntimeResponse;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestRestoreApp;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.restore.BackupNotFoundException;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public interface RestoreAcceptance {
  @Test
  default void shouldRunRestore() {
    // given
    final var backupId = 17;

    // when
    takeBackup(backupId);

    // then
    assertThatNoException().isThrownBy(() -> restoreBackup(backupId));
  }

  @Test
  default void shouldFailForNonExistingBackup() {
    // then -- restore application exits with an error code
    assertThatCode(() -> restoreBackup(1234))
        .isInstanceOf(CompletionException.class)
        .hasRootCauseExactlyInstanceOf(BackupNotFoundException.class);
  }

  private void takeBackup(final long backupId) {
    try (final var zeebe =
        new TestStandaloneBroker()
            .withBrokerConfig(this::configureBackupStore)
            .start()
            .awaitCompleteTopology()) {
      final var actuator = BackupActuator.of(zeebe);

      try (final var client = zeebe.newClientBuilder().build()) {
        client.newPublishMessageCommand().messageName("name").correlationKey("key").send().join();
      }

      final PartitionsActuator partitions = PartitionsActuator.of(zeebe);
      partitions.takeSnapshot();

      Awaitility.await("Snapshot is taken")
          .atMost(Duration.ofSeconds(60))
          .until(
              () ->
                  Optional.ofNullable(partitions.query().get(1).snapshotId())
                      .flatMap(FileBasedSnapshotId::ofFileName),
              Optional::isPresent);

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
  }

  private void restoreBackup(final long backupId) {
    final var restore =
        new TestRestoreApp()
            .withBrokerConfig(this::configureBackupStore)
            .withBackupId(backupId)
            .start();
    restore.close();
  }

  void configureBackupStore(final BrokerCfg cfg);
}
