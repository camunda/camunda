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
import java.time.Duration;
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
public class DataBackupS3Test {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.backup.s3.bucket-name=bucketNameNew",
        "camunda.data.backup.s3.endpoint=endpointNew",
        "camunda.data.backup.s3.region=regionNew",
        "camunda.data.backup.s3.access-key=accessKeyNew",
        "camunda.data.backup.s3.secret-key=secretKeyNew",
        "camunda.data.backup.s3.api-call-timeout=360s",
        "camunda.data.backup.s3.force-path-style-access=true",
        "camunda.data.backup.s3.compression=compressionNew",
        "camunda.data.backup.s3.max-concurrent-connections=100",
        "camunda.data.backup.s3.connection-acquisition-timeout=90s",
        "camunda.data.backup.s3.base-path=basePathNew",
        "camunda.data.backup.s3.support-legacy-md5=true",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetBucketName() {
      assertThat(brokerCfg.getData().getBackup().getS3().getBucketName())
          .isEqualTo("bucketNameNew");
    }

    @Test
    void shouldSetEndpoint() {
      assertThat(brokerCfg.getData().getBackup().getS3().getEndpoint()).isEqualTo("endpointNew");
    }

    @Test
    void shouldSetRegion() {
      assertThat(brokerCfg.getData().getBackup().getS3().getRegion()).isEqualTo("regionNew");
    }

    @Test
    void shouldSetAccessKey() {
      assertThat(brokerCfg.getData().getBackup().getS3().getAccessKey()).isEqualTo("accessKeyNew");
    }

    @Test
    void shouldSetSecretKey() {
      assertThat(brokerCfg.getData().getBackup().getS3().getSecretKey()).isEqualTo("secretKeyNew");
    }

    @Test
    void shouldSetApiCallTimeout() {
      assertThat(brokerCfg.getData().getBackup().getS3().getApiCallTimeout())
          .isEqualTo(Duration.ofSeconds(360));
    }

    @Test
    void shouldSetForcePathStyleAccess() {
      assertThat(brokerCfg.getData().getBackup().getS3().isForcePathStyleAccess()).isTrue();
    }

    @Test
    void shouldSetCompression() {
      assertThat(brokerCfg.getData().getBackup().getS3().getCompression())
          .isEqualTo("compressionNew");
    }

    @Test
    void shouldSetMaxConcurrentConnection() {
      assertThat(brokerCfg.getData().getBackup().getS3().getMaxConcurrentConnections())
          .isEqualTo(100);
    }

    @Test
    void shouldSetConnectionAcquisitionTimeout() {
      assertThat(brokerCfg.getData().getBackup().getS3().getConnectionAcquisitionTimeout())
          .isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void shouldSetBasePath() {
      assertThat(brokerCfg.getData().getBackup().getS3().getBasePath()).isEqualTo("basePathNew");
    }

    @Test
    void shouldSetSupportLegacyMd5() {
      assertThat(brokerCfg.getData().getBackup().getS3().isSupportLegacyMd5()).isTrue();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.data.backup.s3.bucketName=bucketNameLegacy",
        "zeebe.broker.data.backup.s3.endpoint=endpointLegacy",
        "zeebe.broker.data.backup.s3.region=regionLegacy",
        "zeebe.broker.data.backup.s3.accessKey=accessKeyLegacy",
        "zeebe.broker.data.backup.s3.secretKey=secretKeyLegacy",
        "zeebe.broker.data.backup.s3.apiCallTimeout=360s",
        "zeebe.broker.data.backup.s3.forcePathStyleAccess=true",
        "zeebe.broker.data.backup.s3.compression=compressionLegacy",
        "zeebe.broker.data.backup.s3.maxConcurrentConnections=100",
        "zeebe.broker.data.backup.s3.connectionAcquisitionTimeout=90s",
        "zeebe.broker.data.backup.s3.basePath=basePathLegacy",
        "zeebe.broker.data.backup.s3.supportLegacyMd5=true",
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetBucketName() {
      assertThat(brokerCfg.getData().getBackup().getS3().getBucketName())
          .isEqualTo("bucketNameLegacy");
    }

    @Test
    void shouldSetEndpoint() {
      assertThat(brokerCfg.getData().getBackup().getS3().getEndpoint()).isEqualTo("endpointLegacy");
    }

    @Test
    void shouldSetRegion() {
      assertThat(brokerCfg.getData().getBackup().getS3().getRegion()).isEqualTo("regionLegacy");
    }

    @Test
    void shouldSetAccessKey() {
      assertThat(brokerCfg.getData().getBackup().getS3().getAccessKey())
          .isEqualTo("accessKeyLegacy");
    }

    @Test
    void shouldSetSecretKey() {
      assertThat(brokerCfg.getData().getBackup().getS3().getSecretKey())
          .isEqualTo("secretKeyLegacy");
    }

    @Test
    void shouldSetApiCallTimeout() {
      assertThat(brokerCfg.getData().getBackup().getS3().getApiCallTimeout())
          .isEqualTo(Duration.ofSeconds(360));
    }

    @Test
    void shouldSetForcePathStyleAccess() {
      assertThat(brokerCfg.getData().getBackup().getS3().isForcePathStyleAccess()).isTrue();
    }

    @Test
    void shouldSetCompression() {
      assertThat(brokerCfg.getData().getBackup().getS3().getCompression())
          .isEqualTo("compressionLegacy");
    }

    @Test
    void shouldSetMaxConcurrentConnection() {
      assertThat(brokerCfg.getData().getBackup().getS3().getMaxConcurrentConnections())
          .isEqualTo(100);
    }

    @Test
    void shouldSetConnectionAcquisitionTimeout() {
      assertThat(brokerCfg.getData().getBackup().getS3().getConnectionAcquisitionTimeout())
          .isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void shouldSetBasePath() {
      assertThat(brokerCfg.getData().getBackup().getS3().getBasePath()).isEqualTo("basePathLegacy");
    }

    @Test
    void shouldSetSupportLegacyMd5() {
      assertThat(brokerCfg.getData().getBackup().getS3().isSupportLegacyMd5()).isTrue();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.data.backup.s3.bucket-name=bucketNameNew",
        "camunda.data.backup.s3.endpoint=endpointNew",
        "camunda.data.backup.s3.region=regionNew",
        "camunda.data.backup.s3.access-key=accessKeyNew",
        "camunda.data.backup.s3.secret-key=secretKeyNew",
        "camunda.data.backup.s3.api-call-timeout=360s",
        "camunda.data.backup.s3.force-path-style-access=true",
        "camunda.data.backup.s3.compression=compressionNew",
        "camunda.data.backup.s3.max-concurrent-connections=100",
        "camunda.data.backup.s3.connection-acquisition-timeout=90s",
        "camunda.data.backup.s3.base-path=basePathNew",
        "camunda.data.backup.s3.support-legacy-md5=true",
        // legacy
        "zeebe.broker.data.backup.s3.bucketName=bucketNameLegacy",
        "zeebe.broker.data.backup.s3.endpoint=endpointLegacy",
        "zeebe.broker.data.backup.s3.region=regionLegacy",
        "zeebe.broker.data.backup.s3.accessKey=accessKeyLegacy",
        "zeebe.broker.data.backup.s3.secretKey=secretKeyLegacy",
        "zeebe.broker.data.backup.s3.apiCallTimeout=720s",
        "zeebe.broker.data.backup.s3.forcePathStyleAccess=false",
        "zeebe.broker.data.backup.s3.compression=compressionLegacy",
        "zeebe.broker.data.backup.s3.maxConcurrentConnections=200",
        "zeebe.broker.data.backup.s3.connectionAcquisitionTimeout=180s",
        "zeebe.broker.data.backup.s3.basePath=basePathLegacy",
        "zeebe.broker.data.backup.s3.supportLegacyMd5=false",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetBucketNameFromNew() {
      assertThat(brokerCfg.getData().getBackup().getS3().getBucketName())
          .isEqualTo("bucketNameNew");
    }

    @Test
    void shouldSetEndpointFromNew() {
      assertThat(brokerCfg.getData().getBackup().getS3().getEndpoint()).isEqualTo("endpointNew");
    }

    @Test
    void shouldSetRegionFromNew() {
      assertThat(brokerCfg.getData().getBackup().getS3().getRegion()).isEqualTo("regionNew");
    }

    @Test
    void shouldSetAccessKeyFromNew() {
      assertThat(brokerCfg.getData().getBackup().getS3().getAccessKey()).isEqualTo("accessKeyNew");
    }

    @Test
    void shouldSetSecretKeyFromNew() {
      assertThat(brokerCfg.getData().getBackup().getS3().getSecretKey()).isEqualTo("secretKeyNew");
    }

    @Test
    void shouldSetApiCallTimeoutFromNew() {
      assertThat(brokerCfg.getData().getBackup().getS3().getApiCallTimeout())
          .isEqualTo(Duration.ofSeconds(360));
    }

    @Test
    void shouldSetForcePathStyleAccessFromNew() {
      assertThat(brokerCfg.getData().getBackup().getS3().isForcePathStyleAccess()).isTrue();
    }

    @Test
    void shouldSetCompressionFromNew() {
      assertThat(brokerCfg.getData().getBackup().getS3().getCompression())
          .isEqualTo("compressionNew");
    }

    @Test
    void shouldSetMaxConcurrentConnectionFromNew() {
      assertThat(brokerCfg.getData().getBackup().getS3().getMaxConcurrentConnections())
          .isEqualTo(100);
    }

    @Test
    void shouldSetConnectionAcquisitionTimeoutFromNew() {
      assertThat(brokerCfg.getData().getBackup().getS3().getConnectionAcquisitionTimeout())
          .isEqualTo(Duration.ofSeconds(90));
    }

    @Test
    void shouldSetBasePathFromNew() {
      assertThat(brokerCfg.getData().getBackup().getS3().getBasePath()).isEqualTo("basePathNew");
    }

    @Test
    void shouldSetSupportLegacyMd5FromNew() {
      assertThat(brokerCfg.getData().getBackup().getS3().isSupportLegacyMd5()).isTrue();
    }
  }
}
