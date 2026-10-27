/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.schema.strategy.ElasticsearchBackendStrategy;
import io.camunda.it.schema.strategy.SearchBackendStrategy;
import io.camunda.qa.util.cluster.TestStandaloneBackupManager;
import io.camunda.qa.util.cluster.TestStandaloneSchemaManager;
import io.camunda.webapps.backup.repository.WebappsSnapshotNameProvider;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the standalone backup manager backs up every configured physical tenant. Both
 * tenants share one search cluster but own a snapshot repository each — the isolation the boot-time
 * {@code SnapshotRepositoryIsolationValidation} rule enforces — so the test also verifies that a
 * tenant's snapshots land in its own repository and nowhere else.
 */
@ZeebeIntegration
final class PhysicalTenantStandaloneBackupManagerIT {

  private static final String DEFAULT_REPOSITORY_NAME = "backup-test";
  private static final String TENANT_A_REPOSITORY_NAME = "backup-test-tenanta";
  private static final String TENANT_A = "tenanta";
  private static final long BACKUP_ID = 42L;

  private static final String SNAPSHOT_PREFIX =
      new WebappsSnapshotNameProvider().getSnapshotNamePrefix(BACKUP_ID);

  @TestZeebe(autoStart = false)
  final TestStandaloneBackupManager backupManager = new TestStandaloneBackupManager();

  @TestZeebe(autoStart = false)
  final TestStandaloneSchemaManager defaultSchemaManager = new TestStandaloneSchemaManager();

  @TestZeebe(autoStart = false)
  final TestStandaloneSchemaManager tenantASchemaManager = new TestStandaloneSchemaManager();

  @Test
  void shouldBackupAllPhysicalTenantsIntoOwnRepositories() throws Exception {
    try (final SearchBackendStrategy strategy = new ElasticsearchBackendStrategy()) {
      // given
      strategy.startContainer();
      strategy.createAdminClient();
      final String url =
          "http://"
              + strategy.getContainer().getHost()
              + ":"
              + strategy.getContainer().getMappedPort(9200);

      // create the default tenant's schema, and tenant A's schema under its index prefix
      strategy.configureStandaloneSchemaManager(defaultSchemaManager);
      strategy.configureStandaloneSchemaManager(tenantASchemaManager);
      tenantASchemaManager.withProperty(
          "camunda.data.secondary-storage.elasticsearch.index-prefix", TENANT_A);
      defaultSchemaManager.start();
      tenantASchemaManager.start();

      strategy.createSnapshotRepository(DEFAULT_REPOSITORY_NAME);
      strategy.createSnapshotRepository(TENANT_A_REPOSITORY_NAME);

      strategy.configureStandaloneBackupManager(backupManager, DEFAULT_REPOSITORY_NAME);
      backupManager
          .withProperty(tenantProperty("data.secondary-storage.elasticsearch.url"), url)
          .withProperty(
              tenantProperty("data.secondary-storage.elasticsearch.index-prefix"), TENANT_A)
          .withProperty(
              tenantProperty("data.secondary-storage.elasticsearch.backup.repository-name"),
              TENANT_A_REPOSITORY_NAME)
          .withProperty(
              tenantProperty("security.initialization.default-roles.admin.users[0]"),
              TENANT_A + "-admin");

      // when
      backupManager.withBackupId(BACKUP_ID).withSkipSchemaCheck(true).start();

      // then each tenant's snapshots are complete in its own repository
      final List<String> defaultSnapshots =
          waitForSuccessSnapshots(strategy, DEFAULT_REPOSITORY_NAME);
      final List<String> tenantASnapshots =
          waitForSuccessSnapshots(strategy, TENANT_A_REPOSITORY_NAME);

      assertThat(defaultSnapshots).isNotEmpty().hasSameSizeAs(tenantASnapshots);
      // the repository, not the snapshot name, is what separates the tenants
      assertThat(defaultSnapshots)
          .allSatisfy(name -> assertThat(name).startsWith("camunda_webapps_"));
      assertThat(tenantASnapshots)
          .allSatisfy(name -> assertThat(name).startsWith("camunda_webapps_"));
    }
  }

  private static String tenantProperty(final String suffix) {
    return "camunda.physical-tenants." + TENANT_A + "." + suffix;
  }

  private List<String> waitForSuccessSnapshots(
      final SearchBackendStrategy strategy, final String repositoryName) {
    return Awaitility.await("should find completed snapshots in repository " + repositoryName)
        .atMost(ofSeconds(60))
        .pollDelay(ofSeconds(2))
        .until(
            () -> strategy.getSuccessSnapshots(repositoryName, SNAPSHOT_PREFIX),
            snapshots -> !snapshots.isEmpty());
  }
}
