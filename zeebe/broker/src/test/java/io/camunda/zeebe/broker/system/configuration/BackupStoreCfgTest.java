/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.backup.azure.AzureBackupConfig;
import io.camunda.zeebe.backup.gcs.GcsBackupConfig;
import io.camunda.zeebe.backup.s3.S3BackupConfig;
import io.camunda.zeebe.broker.system.configuration.backup.AzureBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig.GcsBackupStoreAuth;
import io.camunda.zeebe.broker.system.configuration.backup.S3BackupStoreConfig;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class BackupStoreCfgTest {

  @Test
  void canConfigureBackupStore() {
    // given
    final var env = Map.of("zeebe.broker.data.backup.store", "gcs");

    // when
    final var cfg = TestConfigReader.readConfig("empty", env);
    // then
    assertThat(cfg.getData().getBackup().getStore()).isEqualTo(BackupStoreType.GCS);
  }

  @Test
  void shouldUseDefaultGcsAuth() {
    // given
    final var env = Map.<String, String>of();

    // when
    final var cfg = TestConfigReader.readConfig("empty", env);
    // then
    assertThat(cfg.getData().getBackup().getGcs().getAuth()).isEqualTo(GcsBackupStoreAuth.AUTO);
  }

  @Test
  void canConfigureGcsAuth() {
    // given
    final var env =
        Map.of(
            "zeebe.broker.data.backup.store", "gcs", "zeebe.broker.data.backup.gcs.auth", "none");

    // when
    final var cfg = TestConfigReader.readConfig("empty", env);
    // then
    assertThat(cfg.getData().getBackup().getGcs().getAuth()).isEqualTo(GcsBackupStoreAuth.NONE);
  }

  @Test
  void canConfigureGcsHost() {
    // given
    final var configuredHost = "localhost";
    final var env =
        Map.of(
            "zeebe.broker.data.backup.store",
            "gcs",
            "zeebe.broker.data.backup.gcs.host",
            configuredHost);

    // when
    final var cfg = TestConfigReader.readConfig("empty", env);
    // then
    assertThat(cfg.getData().getBackup().getGcs().getHost()).isEqualTo(configuredHost);
  }

  @Test
  void shouldLeaveTimeoutsUnsetByDefault() {
    // given
    final var env = Map.<String, String>of();

    // when
    final var backup =
        withBucketNames(TestConfigReader.readConfig("empty", env).getData().getBackup());

    // then
    assertThat(backup.getReadTimeout()).isNull();
    assertThat(backup.getWriteTimeout()).isNull();
    assertThat(s3StoreConfig(backup))
        .extracting(S3BackupConfig::readTimeout, S3BackupConfig::writeTimeout)
        .containsExactly(Optional.empty(), Optional.empty());
    assertThat(gcsStoreConfig(backup))
        .extracting(GcsBackupConfig::readTimeout, GcsBackupConfig::writeTimeout)
        .containsExactly(Optional.empty(), Optional.empty());
    assertThat(azureStoreConfig(backup))
        .extracting(AzureBackupConfig::readTimeout, AzureBackupConfig::writeTimeout)
        .containsExactly(Optional.empty(), Optional.empty());
  }

  @Test
  void shouldPassConfiguredTimeoutsToEveryStore() {
    // given
    final var env =
        Map.of(
            "zeebe.broker.data.backup.readTimeout",
            "90s",
            "zeebe.broker.data.backup.writeTimeout",
            "120s");

    // when
    final var backup =
        withBucketNames(TestConfigReader.readConfig("empty", env).getData().getBackup());

    // then
    final var read = Duration.ofSeconds(90);
    final var write = Duration.ofSeconds(120);
    assertThat(backup.getReadTimeout()).isEqualTo(read);
    assertThat(backup.getWriteTimeout()).isEqualTo(write);
    assertThat(s3StoreConfig(backup))
        .extracting(S3BackupConfig::readTimeout, S3BackupConfig::writeTimeout)
        .containsExactly(Optional.of(read), Optional.of(write));
    assertThat(gcsStoreConfig(backup))
        .extracting(GcsBackupConfig::readTimeout, GcsBackupConfig::writeTimeout)
        .containsExactly(Optional.of(read), Optional.of(write));
    assertThat(azureStoreConfig(backup))
        .extracting(AzureBackupConfig::readTimeout, AzureBackupConfig::writeTimeout)
        .containsExactly(Optional.of(read), Optional.of(write));
  }

  @Test
  void shouldSetPartialS3Config() {
    // given
    final S3BackupStoreConfig expectedConfig = new S3BackupStoreConfig();
    expectedConfig.setBucketName("bucket");
    expectedConfig.setEndpoint("endpoint");
    expectedConfig.setRegion("region-1");
    expectedConfig.setAccessKey(null);
    expectedConfig.setSecretKey(null);

    // when
    final BrokerCfg cfg = TestConfigReader.readConfig("backup-cfg", new HashMap<>());
    final BackupCfg backup = cfg.getData().getBackup();

    // then
    assertThat(backup.getStore()).isEqualTo(BackupStoreType.S3);
    assertThat(backup.getS3()).isEqualTo(expectedConfig);
  }

  /** The S3 and GCS store configs reject a missing bucket name, which is unset by default. */
  private static BackupCfg withBucketNames(final BackupCfg backup) {
    backup.getS3().setBucketName("bucket");
    backup.getGcs().setBucketName("bucket");
    return backup;
  }

  private static S3BackupConfig s3StoreConfig(final BackupCfg backup) {
    return S3BackupStoreConfig.toStoreConfig(
        backup.getS3(), backup.getReadTimeout(), backup.getWriteTimeout());
  }

  private static GcsBackupConfig gcsStoreConfig(final BackupCfg backup) {
    return GcsBackupStoreConfig.toStoreConfig(
        backup.getGcs(), backup.getReadTimeout(), backup.getWriteTimeout());
  }

  private static AzureBackupConfig azureStoreConfig(final BackupCfg backup) {
    return AzureBackupStoreConfig.toStoreConfig(
        backup.getAzure(), backup.getReadTimeout(), backup.getWriteTimeout());
  }
}
