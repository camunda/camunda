/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import feign.FeignException;
import io.camunda.management.backups.BackupInfo;
import io.camunda.management.backups.PartitionBackupInfo;
import io.camunda.management.backups.StateCode;
import io.camunda.management.backups.TakeBackupRuntimeResponse;
import io.camunda.zeebe.qa.util.actuator.BackupActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import java.time.Duration;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.groups.Tuple;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for the backup management API. Tests here should interact with the backups
 * primarily via the management API, and occasionally assert results on the configured backup store.
 *
 * <p>NOTE: this does not test the consistency of backups, nor that partition leaders correctly
 * maintain consistency via checkpoint records. Other test suites should be set up for this.
 */
public interface BackupAcceptance {
  TestCluster getTestCluster();

  @Test
  default void shouldTakeBackup() {
    // given
    final TestCluster cluster = getTestCluster();
    final var actuator = BackupActuator.of(cluster.availableGateway());
    try (final var client = cluster.newClientBuilder().build()) {
      client.newPublishMessageCommand().messageName("name").correlationKey("key").send().join();
    }

    // when
    final var response = actuator.take(1);

    // then
    assertThat(response).isInstanceOf(TakeBackupRuntimeResponse.class);
    waitUntilBackupIsCompleted(actuator, 1L);
  }

  @Test
  default void shouldListBackups() {
    // given
    final TestCluster cluster = getTestCluster();
    final var actuator = BackupActuator.of(cluster.availableGateway());
    try (final var client = cluster.newClientBuilder().build()) {
      client.newPublishMessageCommand().messageName("name").correlationKey("key").send().join();
    }

    // when
    actuator.take(1);
    actuator.take(2);

    waitUntilBackupIsCompleted(actuator, 1L);
    waitUntilBackupIsCompleted(actuator, 2L);

    // then
    final var status = actuator.list();
    assertThat(status)
        .hasSize(2)
        .extracting(BackupInfo::getBackupId, BackupInfo::getState)
        .containsExactly(
            Tuple.tuple(1L, StateCode.COMPLETED), Tuple.tuple(2L, StateCode.COMPLETED));
  }

  @Test
  default void shouldListBackupsByPrefix() {
    // given
    final TestCluster cluster = getTestCluster();
    final var actuator = BackupActuator.of(cluster.availableGateway());
    try (final var client = cluster.newClientBuilder().build()) {
      client.newPublishMessageCommand().messageName("name").correlationKey("key").send().join();
    }

    // when
    actuator.take(100);
    actuator.take(105);
    actuator.take(200);

    waitUntilBackupIsCompleted(actuator, 100);
    waitUntilBackupIsCompleted(actuator, 105);
    waitUntilBackupIsCompleted(actuator, 200);

    // then
    final var status = actuator.list("10*");
    assertThat(status)
        .hasSize(2)
        .extracting(BackupInfo::getBackupId, BackupInfo::getState)
        .containsExactly(
            Tuple.tuple(100L, StateCode.COMPLETED), Tuple.tuple(105L, StateCode.COMPLETED));
  }

  @Test
  default void shouldDeleteBackup() {
    // given
    final TestCluster cluster = getTestCluster();
    final var actuator = BackupActuator.of(cluster.availableGateway());
    final long backupId = 1;
    actuator.take(backupId);
    waitUntilBackupIsCompleted(actuator, backupId);

    // when
    actuator.delete(backupId);

    // then
    Awaitility.await("Backup is deleted")
        .timeout(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThatThrownBy(() -> actuator.status(backupId))
                    .asInstanceOf(InstanceOfAssertFactories.type(FeignException.class))
                    .extracting(FeignException::status)
                    .isEqualTo(404));
  }

  private static void waitUntilBackupIsCompleted(
      final BackupActuator actuator, final long backupId) {
    Awaitility.await("until a backup exists with the id %d".formatted(backupId))
        .atMost(Duration.ofSeconds(30))
        .ignoreExceptions() // 404 NOT_FOUND throws exception
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
