/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static io.camunda.cluster.PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg.BackupStoreType;
import io.camunda.zeebe.restore.PhysicalTenantRestoreConfigurations.PhysicalTenantBrokerConfigurations;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BackupStoreComponentTest {

  private static final String TENANT_A = "tenanta";

  @Test
  void shouldBuildAStorePerPhysicalTenant(@TempDir final Path dir) {
    // given — two tenants, each with its own key space
    final var stores =
        storesFor(
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                filesystemBackup(dir.resolve("default")),
                TENANT_A,
                filesystemBackup(dir.resolve(TENANT_A))));

    // when
    try (stores) {
      final var defaultStore = stores.forPhysicalTenant(DEFAULT_PHYSICAL_TENANT_ID);
      final var tenantAStore = stores.forPhysicalTenant(TENANT_A);

      // then — a store each, and not the same one twice: partition numbers restart at 1 per
      // tenant, so one shared store would have them reading each other's backups
      assertThat(defaultStore).isNotSameAs(tenantAStore);
      assertThat(stores.openStores()).hasSize(2);
    }
  }

  @Test
  void shouldReuseTheStoreItAlreadyOpened(@TempDir final Path dir) {
    // given
    final var stores =
        storesFor(Map.of(DEFAULT_PHYSICAL_TENANT_ID, filesystemBackup(dir.resolve("default"))));

    // when / then — asking twice opens one store, so the restore and its validation share it
    try (stores) {
      assertThat(stores.forPhysicalTenant(DEFAULT_PHYSICAL_TENANT_ID))
          .isSameAs(stores.forPhysicalTenant(DEFAULT_PHYSICAL_TENANT_ID));
      assertThat(stores.openStores()).hasSize(1);
    }
  }

  @Test
  void shouldNotOpenAStoreForATenantNobodyAsksFor(@TempDir final Path dir) {
    // given — a cluster where only tenanta has backups enabled, which the configuration isolation
    // rules permit
    final var noStore = new BrokerCfg();
    noStore.getData().getBackup().setStore(BackupStoreType.NONE);
    final var stores =
        storesFor(
            Map.of(
                DEFAULT_PHYSICAL_TENANT_ID,
                noStore,
                TENANT_A,
                filesystemBackup(dir.resolve(TENANT_A))));

    // when / then — restoring tenanta must not trip over the default tenant's missing store; its
    // data is not this run's business
    try (stores) {
      assertThatCode(() -> stores.forPhysicalTenant(TENANT_A)).doesNotThrowAnyException();
      assertThat(stores.openStores()).hasSize(1);
    }
  }

  @Test
  void shouldRejectATenantWithNoBackupStoreWhenItIsTheOneBeingRestored(@TempDir final Path dir) {
    // given
    final var noStore = new BrokerCfg();
    noStore.getData().getBackup().setStore(BackupStoreType.NONE);
    final var stores = storesFor(Map.of(DEFAULT_PHYSICAL_TENANT_ID, noStore));

    // when / then — named, so an operator knows which tenant to configure
    try (stores) {
      assertThatThrownBy(() -> stores.forPhysicalTenant(DEFAULT_PHYSICAL_TENANT_ID))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(DEFAULT_PHYSICAL_TENANT_ID);
    }
  }

  private static PhysicalTenantBackupStores storesFor(final Map<String, BrokerCfg> configurations) {
    return new BackupStoreComponent(new PhysicalTenantBrokerConfigurations(configurations))
        .backupStores();
  }

  private static BrokerCfg filesystemBackup(final Path basePath) {
    final var configuration = new BrokerCfg();
    final var backup = configuration.getData().getBackup();
    backup.setStore(BackupStoreType.FILESYSTEM);
    backup.getFilesystem().setBasePath(basePath.toString());
    return configuration;
  }
}
