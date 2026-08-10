/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.configuration.Filesystem;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierWildcardImpl;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupConfig;
import io.camunda.zeebe.backup.filesystem.FilesystemBackupStore;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that the checkpoint scheduler runs per physical tenant: every tenant takes scheduled
 * backups on its own schedule and writes them to its own backup store.
 *
 * <p>Before the scheduler became tenant-aware it always targeted the default tenant, so a
 * non-default tenant never got a scheduled backup even with a complete backup configuration.
 */
@Timeout(120)
@ZeebeIntegration
final class PhysicalTenantScheduledBackupIT {

  private static final String TENANT_A = "tenanta";
  private static final Duration CHECKPOINT_INTERVAL = Duration.ofSeconds(1);
  private static final String BACKUP_SCHEDULE = "PT2S";

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_A, Storage.none())
          .build();

  private static @TempDir Path tempDir;

  private final Path defaultBasePath = tempDir.resolve(PhysicalTenantsITHelper.DEFAULT_TENANT_ID);
  private final Path tenantABasePath = tempDir.resolve(TENANT_A);

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS
          .configure(new TestStandaloneBroker().withUnauthenticatedAccess())
          .withUnifiedConfig(
              camunda ->
                  configureBackup(
                      camunda.getData().getPrimaryStorage().getBackup(), defaultBasePath))
          .withPtConfig(
              TENANT_A,
              camunda ->
                  configureBackup(
                      camunda.getData().getPrimaryStorage().getBackup(), tenantABasePath));

  private BackupStore defaultStore;
  private BackupStore tenantAStore;

  @AfterEach
  void tearDown() {
    if (defaultStore != null) {
      defaultStore.closeAsync().join();
    }
    if (tenantAStore != null) {
      tenantAStore.closeAsync().join();
    }
  }

  @Test
  void shouldTakeScheduledBackupsForEveryPhysicalTenant() {
    // given — each tenant writes to its own backup store
    defaultStore = backupStore(defaultBasePath);
    tenantAStore = backupStore(tenantABasePath);

    // when — the schedulers of both tenants have had time to run
    // then — each store holds completed backups of its own tenant's partitions
    await()
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              assertThat(completedBackupCount(defaultStore)).isPositive();
              assertThat(completedBackupCount(tenantAStore)).isPositive();
            });
  }

  private static void configureBackup(final PrimaryStorageBackup backup, final Path basePath) {
    backup.setStore(BackupStoreType.FILESYSTEM);
    final var filesystem = new Filesystem();
    filesystem.setBasePath(basePath.toAbsolutePath().toString());
    backup.setFilesystem(filesystem);
    backup.setContinuous(true);
    backup.setCheckpointInterval(CHECKPOINT_INTERVAL);
    backup.setSchedule(BACKUP_SCHEDULE);
  }

  private static BackupStore backupStore(final Path basePath) {
    return FilesystemBackupStore.of(
        new FilesystemBackupConfig(basePath.toAbsolutePath().toString()));
  }

  private static long completedBackupCount(final BackupStore store) {
    return store
        .list(
            new BackupIdentifierWildcardImpl(
                Optional.empty(), Optional.empty(), CheckpointPattern.any()))
        .join()
        .stream()
        .filter(status -> status.statusCode() == BackupStatusCode.COMPLETED)
        .count();
  }
}
