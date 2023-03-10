/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.backup.GCSBackupStoreConfig.GcsBackupStoreAuth;
import io.camunda.zeebe.broker.system.configuration.backup.S3BackupStoreConfig;
import java.util.HashMap;
import java.util.Map;
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
    final BackupStoreCfg backup = cfg.getData().getBackup();

    // then
    assertThat(backup.getStore()).isEqualTo(BackupStoreType.S3);
    assertThat(backup.getS3()).isEqualTo(expectedConfig);
  }
}
