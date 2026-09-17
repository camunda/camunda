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

import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.dynamic.nodeid.fs.DataDirectoryProvider;
import io.camunda.zeebe.restore.PhysicalTenantRestoreConfigurations.PhysicalTenantBrokerConfigurations;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;

/**
 * The per-tenant {@code BrokerCfg}s the restore application derives.
 *
 * <p>These feed the generated cluster configuration, so they have to agree with what the broker
 * will derive for the same tenants on boot — see {@link PhysicalTenantRestoreConfigurations}.
 */
final class PhysicalTenantRestoreConfigurationsTest {

  private static final String TENANT_A = "tenanta";

  @Test
  void shouldDeriveAConfigurationPerPhysicalTenant(@TempDir final Path dir) {
    // given — a second tenant overriding its partition count
    final var root = rootConfiguration(dir, 3);
    final var resolver = resolver(root.getCluster().getPartitionsCount(), 2);

    // when
    final var configurations = configurations(resolver, root, dir);

    // then
    assertThat(configurations.physicalTenantIds())
        .containsExactlyInAnyOrder(DEFAULT_PHYSICAL_TENANT_ID, TENANT_A);
    assertThat(configurations.forPhysicalTenant(TENANT_A).getCluster().getPartitionsCount())
        .isEqualTo(2);
    assertThat(
            configurations
                .forPhysicalTenant(DEFAULT_PHYSICAL_TENANT_ID)
                .getCluster()
                .getPartitionsCount())
        .isEqualTo(3);
  }

  @Test
  void shouldGiveTheDefaultTenantTheRootConfigurationItself(@TempDir final Path dir) {
    // given
    final var root = rootConfiguration(dir, 1);

    // when
    final var configurations = configurations(resolver(1, 1), root, dir);

    // then — the very same instance, not a converted copy: that is what keeps legacy
    // `zeebe.broker.*` properties applying to the default tenant
    assertThat(configurations.forPhysicalTenant(DEFAULT_PHYSICAL_TENANT_ID)).isSameAs(root);
  }

  @Test
  void shouldGiveEveryTenantTheNodeLevelDataDirectory(@TempDir final Path dir) {
    // given — the root's directory is the one already resolved for this node
    final var root = rootConfiguration(dir, 1);

    // when
    final var configurations = configurations(resolver(1, 1), root, dir);

    // then — a tenant re-deriving its own directory would look for its partitions in the wrong
    // place; they live under one node directory, separated by their partition-group segment
    assertThat(configurations.forPhysicalTenant(TENANT_A).getData().getDirectory())
        .isEqualTo(root.getData().getDirectory());
  }

  @Test
  void shouldResolveRelativeTenantPathsAgainstTheWorkingDirectory(@TempDir final Path dir) {
    // given — the node's data directory is somewhere below the working directory, and the tenant
    // configures a relative runtime directory
    final var workingDirectory = dir.resolve("work");
    final var root = rootConfiguration(dir.resolve("data"), 1);

    // when
    final var configurations = configurations(resolver(1, 1, "runtime"), root, workingDirectory);

    // then — resolved against the working directory, which is the base BrokerCfg#init fans out to
    // every relative path in the configuration; resolving against the data directory instead would
    // put this tenant's runtime state inside the restored partition data
    assertThat(configurations.forPhysicalTenant(TENANT_A).getData().getRuntimeDirectory())
        .isEqualTo(workingDirectory.toAbsolutePath().resolve("runtime").toString());
  }

  /**
   * The per-tenant restore environment ({@code databaseType}, {@code continuousBackups}) that
   * {@link RestoreApp#validateParameters} validates each tenant's request against.
   *
   * <p>Regression coverage for reading these from the root {@code Camunda} config instead of each
   * tenant's own: nothing constrains {@code continuousBackups} to agree across tenants, so a
   * cluster where the default tenant does not take continuous backups but another tenant does (or
   * vice versa) is a valid, reachable configuration — and validating one tenant's restore request
   * with another tenant's setting would wrongly accept or reject a time-range restore.
   */
  @Test
  void shouldDeriveARestoreEnvironmentPerPhysicalTenant(@TempDir final Path dir) {
    // given — the default tenant does not take continuous backups, tenanta does
    final var root = rootConfiguration(dir, 1);
    final var resolver = resolverWithContinuousBackups(false, true);

    // when
    final var environments =
        new PhysicalTenantRestoreConfigurations().physicalTenantRestoreEnvironments(resolver);

    // then — each tenant's own setting, not the root's applied to both
    assertThat(environments.forPhysicalTenant(DEFAULT_PHYSICAL_TENANT_ID).continuousBackups())
        .isFalse();
    assertThat(environments.forPhysicalTenant(TENANT_A).continuousBackups()).isTrue();
  }

  private static PhysicalTenantResolver resolverWithContinuousBackups(
      final boolean rootContinuousBackups, final boolean tenantContinuousBackups) {
    final var camunda = new Camunda();
    final var rootBackup = camunda.getData().getPrimaryStorage().getBackup();
    rootBackup.setStore(BackupStoreType.FILESYSTEM);
    rootBackup.getFilesystem().setBasePath("/tmp/restore-pt-test/default");
    rootBackup.setContinuous(rootContinuousBackups);

    final var environment = new MockEnvironment();
    final var base = "camunda.physical-tenants." + TENANT_A + ".";
    environment.setProperty(base + "data.secondary-storage.elasticsearch.index-prefix", TENANT_A);
    environment.setProperty(base + "data.primary-storage.backup.store", "filesystem");
    environment.setProperty(
        base + "data.primary-storage.backup.filesystem.basepath",
        "/tmp/restore-pt-test/" + TENANT_A);
    environment.setProperty(
        base + "data.primary-storage.backup.continuous", String.valueOf(tenantContinuousBackups));
    environment.setProperty(
        base + "security.initialization.default-roles.admin.users[0]", TENANT_A + "-admin");
    return PhysicalTenantResolver.of(environment, camunda);
  }

  private static PhysicalTenantBrokerConfigurations configurations(
      final PhysicalTenantResolver resolver,
      final BrokerBasedProperties root,
      final Path workingDirectory) {
    return new PhysicalTenantRestoreConfigurations()
        .physicalTenantConfigurations(
            resolver,
            resolver.forPhysicalTenant(DEFAULT_PHYSICAL_TENANT_ID),
            root,
            NodeIdProvider.staticProvider(0),
            new WorkingDirectory(workingDirectory, false),
            Mockito.mock(DataDirectoryProvider.class));
  }

  private static BrokerBasedProperties rootConfiguration(final Path dir, final int partitionCount) {
    final var root = new BrokerBasedProperties();
    root.getData().setDirectory(dir.toString());
    root.getCluster().setPartitionsCount(partitionCount);
    return root;
  }

  /**
   * A resolver with the default tenant plus {@link #TENANT_A}.
   *
   * <p>Every isolation-relevant location is set explicitly on both tenants, not left at its
   * default: the cross-tenant validations reject two tenants sharing a backup or secondary-storage
   * location, and a default inherited from whatever configuration another test in this JVM left
   * behind would make that rejection depend on test ordering.
   */
  private static PhysicalTenantResolver resolver(
      final int rootPartitionCount, final int tenantPartitionCount) {
    return resolver(rootPartitionCount, tenantPartitionCount, null);
  }

  private static PhysicalTenantResolver resolver(
      final int rootPartitionCount,
      final int tenantPartitionCount,
      final String tenantRuntimeDirectory) {
    final var camunda = new Camunda();
    camunda.getCluster().setPartitionCount(rootPartitionCount);
    final var rootBackup = camunda.getData().getPrimaryStorage().getBackup();
    rootBackup.setStore(BackupStoreType.FILESYSTEM);
    rootBackup.getFilesystem().setBasePath("/tmp/restore-pt-test/default");

    final var environment = new MockEnvironment();
    final var base = "camunda.physical-tenants." + TENANT_A + ".";
    environment.setProperty(base + "cluster.partition-count", String.valueOf(tenantPartitionCount));
    environment.setProperty(base + "data.secondary-storage.elasticsearch.index-prefix", TENANT_A);
    environment.setProperty(base + "data.primary-storage.backup.store", "filesystem");
    environment.setProperty(
        base + "data.primary-storage.backup.filesystem.basepath",
        "/tmp/restore-pt-test/" + TENANT_A);
    environment.setProperty(
        base + "security.initialization.default-roles.admin.users[0]", TENANT_A + "-admin");
    if (tenantRuntimeDirectory != null) {
      environment.setProperty(
          base + "data.primary-storage.runtime-directory", tenantRuntimeDirectory);
    }
    return PhysicalTenantResolver.of(environment, camunda);
  }
}
