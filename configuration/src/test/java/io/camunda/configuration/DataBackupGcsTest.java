/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig.GcsBackupStoreAuth;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class DataBackupGcsTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.backup.gcs.bucket-name=bucketNameNew",
        "camunda.data.backup.gcs.base-path=basePathNew",
        "camunda.data.backup.gcs.host=hostNew",
        "camunda.data.backup.gcs.auth=none",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetBucketName() {
      assertThat(brokerCfg.getData().getBackup().getGcs().getBucketName())
          .isEqualTo("bucketNameNew");
    }

    @Test
    void shouldSetBasePath() {
      assertThat(brokerCfg.getData().getBackup().getGcs().getBasePath()).isEqualTo("basePathNew");
    }

    @Test
    void shouldSetHost() {
      assertThat(brokerCfg.getData().getBackup().getGcs().getHost()).isEqualTo("hostNew");
    }

    @Test
    void shouldSetAuth() {
      assertThat(brokerCfg.getData().getBackup().getGcs().getAuth())
          .isEqualTo(GcsBackupStoreAuth.NONE);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.data.backup.gcs.bucketName=bucketNameLegacy",
        "zeebe.broker.data.backup.gcs.basePath=basePathLegacy",
        "zeebe.broker.data.backup.gcs.host=hostLegacy",
        "zeebe.broker.data.backup.gcs.auth=none",
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetBucketName() {
      assertThat(brokerCfg.getData().getBackup().getGcs().getBucketName())
          .isEqualTo("bucketNameLegacy");
    }

    @Test
    void shouldSetBasePath() {
      assertThat(brokerCfg.getData().getBackup().getGcs().getBasePath())
          .isEqualTo("basePathLegacy");
    }

    @Test
    void shouldSetHost() {
      assertThat(brokerCfg.getData().getBackup().getGcs().getHost()).isEqualTo("hostLegacy");
    }

    @Test
    void shouldSetAuth() {
      assertThat(brokerCfg.getData().getBackup().getGcs().getAuth())
          .isEqualTo(GcsBackupStoreAuth.NONE);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.data.backup.gcs.bucket-name=bucketNameNew",
        "camunda.data.backup.gcs.base-path=basePathNew",
        "camunda.data.backup.gcs.host=hostNew",
        "camunda.data.backup.gcs.auth=none",
        // legacy
        "zeebe.broker.data.backup.gcs.bucketName=bucketNameLegacy",
        "zeebe.broker.data.backup.gcs.basePath=basePathLegacy",
        "zeebe.broker.data.backup.gcs.host=hostLegacy",
        "zeebe.broker.data.backup.gcs.auth=auto",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetBucketNameFromNew() {
      assertThat(brokerCfg.getData().getBackup().getGcs().getBucketName())
          .isEqualTo("bucketNameNew");
    }

    @Test
    void shouldSetBasePathFromNew() {
      assertThat(brokerCfg.getData().getBackup().getGcs().getBasePath()).isEqualTo("basePathNew");
    }

    @Test
    void shouldSetHostFromNew() {
      assertThat(brokerCfg.getData().getBackup().getGcs().getHost()).isEqualTo("hostNew");
    }

    @Test
    void shouldSetAuthFromNew() {
      assertThat(brokerCfg.getData().getBackup().getGcs().getAuth())
          .isEqualTo(GcsBackupStoreAuth.NONE);
    }
  }
}
