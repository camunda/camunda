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
import io.camunda.zeebe.broker.system.configuration.engine.CachesCfg;
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
public class EngineCachesTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.processing.engine.caches.drg-cache-capacity=10",
        "camunda.processing.engine.caches.form-cache-capacity=20",
        "camunda.processing.engine.caches.process-cache-capacity=30",
        "camunda.processing.engine.caches.resource-cache-capacity=40",
        "camunda.processing.engine.caches.authorizations-cache-capacity=50",
        "camunda.processing.engine.caches.authorizations-cache-ttl=5s",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetCaches() {
      assertThat(brokerCfg.getExperimental().getEngine().getCaches())
          .returns(10, CachesCfg::getDrgCacheCapacity)
          .returns(20, CachesCfg::getFormCacheCapacity)
          .returns(30, CachesCfg::getProcessCacheCapacity)
          .returns(40, CachesCfg::getResourceCacheCapacity)
          .returns(50, CachesCfg::getAuthorizationsCacheCapacity)
          .returns(Duration.ofSeconds(5), CachesCfg::getAuthorizationsCacheTtl);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.experimental.engine.caches.drgCacheCapacity=10",
        "zeebe.broker.experimental.engine.caches.formCacheCapacity=20",
        "zeebe.broker.experimental.engine.caches.processCacheCapacity=30",
        "zeebe.broker.experimental.engine.caches.resourceCacheCapacity=40",
        "zeebe.broker.experimental.engine.caches.authorizationsCacheCapacity=50",
        "zeebe.broker.experimental.engine.caches.authorizationsCacheTtl=5s",
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetCachesFromLegacy() {
      assertThat(brokerCfg.getExperimental().getEngine().getCaches())
          .returns(10, CachesCfg::getDrgCacheCapacity)
          .returns(20, CachesCfg::getFormCacheCapacity)
          .returns(30, CachesCfg::getProcessCacheCapacity)
          .returns(40, CachesCfg::getResourceCacheCapacity)
          .returns(50, CachesCfg::getAuthorizationsCacheCapacity)
          .returns(Duration.ofSeconds(5), CachesCfg::getAuthorizationsCacheTtl);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.processing.engine.caches.drg-cache-capacity=10",
        "camunda.processing.engine.caches.form-cache-capacity=20",
        "camunda.processing.engine.caches.process-cache-capacity=30",
        "camunda.processing.engine.caches.resource-cache-capacity=40",
        "camunda.processing.engine.caches.authorizations-cache-capacity=50",
        "camunda.processing.engine.caches.authorizations-cache-ttl=5s",
        // legacy
        "zeebe.broker.experimental.engine.caches.drgCacheCapacity=100",
        "zeebe.broker.experimental.engine.caches.formCacheCapacity=200",
        "zeebe.broker.experimental.engine.caches.processCacheCapacity=300",
        "zeebe.broker.experimental.engine.caches.resourceCacheCapacity=400",
        "zeebe.broker.experimental.engine.caches.authorizationsCacheCapacity=500",
        "zeebe.broker.experimental.engine.caches.authorizationsCacheTtl=50s",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetCachesFromNew() {
      assertThat(brokerCfg.getExperimental().getEngine().getCaches())
          .returns(10, CachesCfg::getDrgCacheCapacity)
          .returns(20, CachesCfg::getFormCacheCapacity)
          .returns(30, CachesCfg::getProcessCacheCapacity)
          .returns(40, CachesCfg::getResourceCacheCapacity)
          .returns(50, CachesCfg::getAuthorizationsCacheCapacity)
          .returns(Duration.ofSeconds(5), CachesCfg::getAuthorizationsCacheTtl);
    }
  }
}
