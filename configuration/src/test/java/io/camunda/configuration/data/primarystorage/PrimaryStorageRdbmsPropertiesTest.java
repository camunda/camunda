/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.data.primarystorage;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.DataCfg.PrimaryStorageType;
import io.camunda.zeebe.broker.system.configuration.RdbmsStorageCfg;
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
public class PrimaryStorageRdbmsPropertiesTest {

  @Nested
  class WithNothingSet {
    final BrokerBasedProperties brokerCfg;

    WithNothingSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldKeepTheStateInRocksDb() {
      assertThat(brokerCfg.getData().getPrimaryStorageType()).isEqualTo(PrimaryStorageType.ROCKSDB);
    }

    @Test
    void shouldUseTheBrokerDefaults() {
      assertThat(brokerCfg.getData().getRdbms())
          .usingRecursiveComparison()
          .isEqualTo(new RdbmsStorageCfg());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.primary-storage.type=rdbms",
        "camunda.data.primary-storage.rdbms.url=jdbc:postgresql://localhost:5432/state",
        "camunda.data.primary-storage.rdbms.username=camunda",
        "camunda.data.primary-storage.rdbms.password=secret",
        "camunda.data.primary-storage.rdbms.prefix=C1_",
        "camunda.data.primary-storage.rdbms.connection-pool.maximum-pool-size=20",
        "camunda.data.primary-storage.rdbms.connection-pool.minimum-idle=5",
        "camunda.data.primary-storage.rdbms.connection-pool.connection-timeout=10s",
        "camunda.data.primary-storage.rdbms.connection-pool.idle-timeout=5m",
        "camunda.data.primary-storage.rdbms.connection-pool.max-lifetime=20m",
        "camunda.data.primary-storage.rdbms.connection-pool.leak-detection-threshold=1m",
        "camunda.data.primary-storage.rdbms.connection-pool.keepalive-time=2m",
        "camunda.data.primary-storage.rdbms.connection-pool.validation-timeout=3s"
      })
  class WithUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetType() {
      assertThat(brokerCfg.getData().getPrimaryStorageType()).isEqualTo(PrimaryStorageType.RDBMS);
    }

    @Test
    void shouldSetConnection() {
      final var rdbms = brokerCfg.getData().getRdbms();
      assertThat(rdbms.getUrl()).isEqualTo("jdbc:postgresql://localhost:5432/state");
      assertThat(rdbms.getUsername()).isEqualTo("camunda");
      assertThat(rdbms.getPassword()).isEqualTo("secret");
      assertThat(rdbms.getPrefix()).isEqualTo("C1_");
    }

    @Test
    void shouldSetConnectionPool() {
      final var pool = brokerCfg.getData().getRdbms().getConnectionPool();
      assertThat(pool.getMaximumPoolSize()).isEqualTo(20);
      assertThat(pool.getMinimumIdle()).isEqualTo(5);
      assertThat(pool.getConnectionTimeout()).isEqualTo(Duration.ofSeconds(10));
      assertThat(pool.getIdleTimeout()).isEqualTo(Duration.ofMinutes(5));
      assertThat(pool.getMaxLifetime()).isEqualTo(Duration.ofMinutes(20));
      assertThat(pool.getLeakDetectionThreshold()).isEqualTo(Duration.ofMinutes(1));
      assertThat(pool.getKeepaliveTime()).isEqualTo(Duration.ofMinutes(2));
      assertThat(pool.getValidationTimeout()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void shouldNotShowThePassword() {
      assertThat(brokerCfg.getData().toString()).doesNotContain("secret");
    }
  }
}
