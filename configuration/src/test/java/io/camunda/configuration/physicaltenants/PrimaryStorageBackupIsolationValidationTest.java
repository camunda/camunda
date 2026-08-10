/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.Filesystem;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.configuration.S3;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Unit tests for the {@link PrimaryStorageBackupIsolationValidation} cross-tenant rule: no two
 * physical tenants may resolve to overlapping backup key spaces, because backup keys name a
 * partition but never a tenant.
 */
class PrimaryStorageBackupIsolationValidationTest {

  private final PrimaryStorageBackupIsolationValidation validation =
      new PrimaryStorageBackupIsolationValidation();

  @BeforeEach
  void setUp() {
    UnifiedConfigurationHelper.setCustomEnvironment(new MockEnvironment());
  }

  @AfterEach
  void tearDown() {
    UnifiedConfigurationHelper.setCustomEnvironment(null);
  }

  @Test
  void shouldRejectTenantsInheritingOneRootBackupStore() {
    // given two tenants that both leave the root backup configuration untouched — the default, and
    // the reason this rule exists
    final var resolved =
        tenants("tenanta", filesystem("/backups"), "tenantb", filesystem("/backups"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb")
        .withMessageContaining("/backups");
  }

  @Test
  void shouldRejectTenantNestedInsideAnotherTenantsDirectory() {
    // given tenantb's base path sits inside tenanta's directory tree
    final var resolved =
        tenants("tenanta", filesystem("/backups"), "tenantb", filesystem("/backups/tenantb"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb");
  }

  @Test
  void shouldRejectTenantsSharingBucketAndBasePathPrefix() {
    // given both tenants write to one bucket, one base path a prefix of the other; `/` bounds
    // nothing in an S3 key, so `backups` reaches every key under `backups/tenantb`
    final var resolved =
        tenants(
            "tenanta", s3("shared-bucket", "backups"),
            "tenantb", s3("shared-bucket", "backups/tenantb"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("shared-bucket");
  }

  @Test
  void shouldAllowTenantsWithDistinctBasePaths() {
    // given each tenant has its own directory, neither inside the other
    final var resolved =
        tenants(
            "tenanta", filesystem("/backups/tenanta"), "tenantb", filesystem("/backups/tenantb"));

    // when / then
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldNotConfuseSiblingDirectoriesSharingAPrefix() {
    // given directory names where one string is a prefix of the other but the directories are
    // siblings — on a file system, unlike in an object key, the segment boundary is real
    final var resolved =
        tenants("tenanta", filesystem("/backups"), "tenantb", filesystem("/backups-archive"));

    // when / then
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldAllowTenantsWithoutABackupStore() {
    // given tenants that take no backups at all, so they share no keys
    final var resolved = tenants("tenanta", noStore(), "tenantb", noStore());

    // when / then
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  @Test
  void shouldAllowASingleTenantWithABackupStore() {
    // given the common single-tenant deployment
    final var resolved = new LinkedHashMap<String, Camunda>();
    resolved.put("default", camunda(filesystem("/backups")));

    // when / then
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
  }

  private Map<String, Camunda> tenants(
      final String firstId,
      final Consumer<PrimaryStorageBackup> first,
      final String secondId,
      final Consumer<PrimaryStorageBackup> second) {
    final var resolved = new LinkedHashMap<String, Camunda>();
    resolved.put(firstId, camunda(first));
    resolved.put(secondId, camunda(second));
    return resolved;
  }

  private Camunda camunda(final Consumer<PrimaryStorageBackup> backupConfig) {
    final var camunda = new Camunda();
    backupConfig.accept(camunda.getData().getPrimaryStorage().getBackup());
    return camunda;
  }

  private Consumer<PrimaryStorageBackup> filesystem(final String basePath) {
    return backup -> {
      backup.setStore(BackupStoreType.FILESYSTEM);
      final var filesystem = new Filesystem();
      filesystem.setBasePath(basePath);
      backup.setFilesystem(filesystem);
    };
  }

  private Consumer<PrimaryStorageBackup> s3(final String bucketName, final String basePath) {
    return backup -> {
      backup.setStore(BackupStoreType.S3);
      final var s3 = new S3();
      s3.setBucketName(bucketName);
      s3.setBasePath(basePath);
      backup.setS3(s3);
    };
  }

  private Consumer<PrimaryStorageBackup> noStore() {
    return backup -> backup.setStore(BackupStoreType.NONE);
  }
}
