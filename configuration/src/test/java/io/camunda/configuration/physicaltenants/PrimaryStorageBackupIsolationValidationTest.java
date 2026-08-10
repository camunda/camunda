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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.configuration.Azure;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Filesystem;
import io.camunda.configuration.Gcs;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.configuration.PrimaryStorageBackup.BackupStoreType;
import io.camunda.configuration.S3;
import io.camunda.configuration.UnifiedConfigurationException;
import io.camunda.configuration.UnifiedConfigurationHelper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("sharedLocationsPerStore")
  void shouldRejectTenantsSharingALocation(
      final String store, final Consumer<PrimaryStorageBackup> shared) {
    // given both tenants resolve to the same location, whichever store they use
    final var resolved = tenants("tenanta", shared, "tenantb", shared);

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb")
        .withMessageContaining("store=" + store);
  }

  static Stream<Arguments> sharedLocationsPerStore() {
    return Stream.of(
        arguments("filesystem", filesystem("/backups")),
        arguments("s3", s3("shared-bucket", "backups")),
        arguments("gcs", gcs("shared-bucket", "backups")),
        arguments("azure", azure("shared-container")));
  }

  @Test
  void shouldNormalizeTheGcsBasePathTheWayTheStoreDoes() {
    // given base paths that differ only in the surrounding slashes GcsBackupConfig strips, so both
    // resolve to the same prefix
    final var resolved =
        tenants(
            "tenanta", gcs("shared-bucket", "/backups/"),
            "tenantb", gcs("shared-bucket", "backups"));

    // when / then
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("tenanta")
        .withMessageContaining("tenantb");
  }

  @Test
  void shouldNotReportCredentialsCarriedInAnEndpoint() {
    // given an endpoint that carries a token, as a SAS-style URL does
    final var resolved =
        tenants(
            "tenanta",
                azure("shared-container", "https://account.blob.core.windows.net?sig=secret"),
            "tenantb",
                azure("shared-container", "https://account.blob.core.windows.net?sig=secret"));

    // when / then the collision is reported without the query string it was configured with
    assertThatExceptionOfType(UnifiedConfigurationException.class)
        .isThrownBy(() -> validation.validate(resolved))
        .withMessageContaining("account.blob.core.windows.net")
        .withMessageNotContaining("sig=secret");
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
  void shouldAllowBasePathsThatOnlyShareAStringPrefix() {
    // given base paths where one string starts with the other, but the keys below them cannot
    // overlap: every backup key continues as `<basePath>/<partitionId>/…`
    final var resolved =
        tenants(
            "tenanta", s3("shared-bucket", "backups"),
            "tenantb", s3("shared-bucket", "backups-tenantb"));

    // when / then
    assertThatCode(() -> validation.validate(resolved)).doesNotThrowAnyException();
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

  private static Map<String, Camunda> tenants(
      final String firstId,
      final Consumer<PrimaryStorageBackup> first,
      final String secondId,
      final Consumer<PrimaryStorageBackup> second) {
    final var resolved = new LinkedHashMap<String, Camunda>();
    resolved.put(firstId, camunda(first));
    resolved.put(secondId, camunda(second));
    return resolved;
  }

  private static Camunda camunda(final Consumer<PrimaryStorageBackup> backupConfig) {
    final var camunda = new Camunda();
    backupConfig.accept(camunda.getData().getPrimaryStorage().getBackup());
    return camunda;
  }

  private static Consumer<PrimaryStorageBackup> filesystem(final String basePath) {
    return backup -> {
      backup.setStore(BackupStoreType.FILESYSTEM);
      final var filesystem = new Filesystem();
      filesystem.setBasePath(basePath);
      backup.setFilesystem(filesystem);
    };
  }

  private static Consumer<PrimaryStorageBackup> s3(final String bucketName, final String basePath) {
    return backup -> {
      backup.setStore(BackupStoreType.S3);
      final var s3 = new S3();
      s3.setBucketName(bucketName);
      s3.setBasePath(basePath);
      backup.setS3(s3);
    };
  }

  private static Consumer<PrimaryStorageBackup> gcs(
      final String bucketName, final String basePath) {
    return backup -> {
      backup.setStore(BackupStoreType.GCS);
      final var gcs = new Gcs();
      gcs.setBucketName(bucketName);
      gcs.setBasePath(basePath);
      backup.setGcs(gcs);
    };
  }

  private static Consumer<PrimaryStorageBackup> azure(final String containerName) {
    return azure(containerName, "https://account.blob.core.windows.net");
  }

  /** Azure names its container with the base path, so that is what identifies the location. */
  private static Consumer<PrimaryStorageBackup> azure(
      final String containerName, final String endpoint) {
    return backup -> {
      backup.setStore(BackupStoreType.AZURE);
      final var azure = new Azure();
      azure.setBasePath(containerName);
      azure.setEndpoint(endpoint);
      backup.setAzure(azure);
    };
  }

  private static Consumer<PrimaryStorageBackup> noStore() {
    return backup -> backup.setStore(BackupStoreType.NONE);
  }
}
