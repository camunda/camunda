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
import io.camunda.zeebe.broker.system.configuration.WriteBehindCacheCfg;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.util.unit.DataSize;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class WriteBehindCachePropertiesTest {

  @Nested
  class WithNothingSet {
    final BrokerBasedProperties brokerCfg;

    WithNothingSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldKeepTheCacheDisabled() {
      assertThat(brokerCfg.getData().getWriteBehindCache().isEnabled()).isFalse();
    }

    @Test
    void shouldUseTheBrokerDefaults() {
      assertThat(brokerCfg.getData().getWriteBehindCache())
          .usingRecursiveComparison()
          .isEqualTo(new WriteBehindCacheCfg());
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.primary-storage.write-behind-cache.enabled=true",
        "camunda.data.primary-storage.write-behind-cache.cache-size=128MB",
        "camunda.data.primary-storage.write-behind-cache.max-pending-size=16MB",
        "camunda.data.primary-storage.write-behind-cache.batch-size=500",
        "camunda.data.primary-storage.write-behind-cache.max-write-delay=50ms",
        "camunda.data.primary-storage.write-behind-cache.snapshot-catch-up-timeout=1m"
      })
  class WithUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetEnabled() {
      assertThat(brokerCfg.getData().getWriteBehindCache().isEnabled()).isTrue();
    }

    @Test
    void shouldSetCacheSize() {
      assertThat(brokerCfg.getData().getWriteBehindCache().getCacheSize())
          .isEqualTo(DataSize.ofMegabytes(128));
    }

    @Test
    void shouldSetMaxPendingSize() {
      assertThat(brokerCfg.getData().getWriteBehindCache().getMaxPendingSize())
          .isEqualTo(DataSize.ofMegabytes(16));
    }

    @Test
    void shouldSetBatchSize() {
      assertThat(brokerCfg.getData().getWriteBehindCache().getBatchSize()).isEqualTo(500);
    }

    @Test
    void shouldSetMaxWriteDelay() {
      assertThat(brokerCfg.getData().getWriteBehindCache().getMaxWriteDelay())
          .isEqualTo(Duration.ofMillis(50));
    }

    @Test
    void shouldSetSnapshotCatchUpTimeout() {
      assertThat(brokerCfg.getData().getWriteBehindCache().getSnapshotCatchUpTimeout())
          .isEqualTo(Duration.ofMinutes(1));
    }
  }
}
