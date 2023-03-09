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
import io.camunda.zeebe.broker.system.configuration.backup.S3BackupStoreConfig;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

final class BackupStoreCfgTest {

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
