/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.data.primarystorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.UnifiedConfigurationHelper;
import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
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
  class WithDefaults {
    final BrokerBasedProperties brokerCfg;

    WithDefaults(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldKeepEngineStateInRocksDb() {
      assertThat(brokerCfg.getData().getRdbmsEngineState().isEnabled()).isFalse();
    }

    @Test
    void shouldUseSmallPoolByDefault() {
      assertThat(brokerCfg.getData().getRdbmsEngineState().getPoolSize()).isEqualTo(4);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.primary-storage.type=rdbms",
        "camunda.data.primary-storage.rdbms.url=jdbc:postgresql://localhost:5432/camunda",
        "camunda.data.primary-storage.rdbms.username=camunda",
        "camunda.data.primary-storage.rdbms.password=secret",
        "camunda.data.primary-storage.rdbms.pool-size=8"
      })
  class WithRdbmsType {
    final BrokerBasedProperties brokerCfg;

    WithRdbmsType(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldEnableRdbmsEngineState() {
      assertThat(brokerCfg.getData().getRdbmsEngineState().isEnabled()).isTrue();
    }

    @Test
    void shouldSetUrl() {
      assertThat(brokerCfg.getData().getRdbmsEngineState().getUrl())
          .isEqualTo("jdbc:postgresql://localhost:5432/camunda");
    }

    @Test
    void shouldSetUsername() {
      assertThat(brokerCfg.getData().getRdbmsEngineState().getUsername()).isEqualTo("camunda");
    }

    @Test
    void shouldSetPassword() {
      assertThat(brokerCfg.getData().getRdbmsEngineState().getPassword()).isEqualTo("secret");
    }

    @Test
    void shouldSetPoolSize() {
      assertThat(brokerCfg.getData().getRdbmsEngineState().getPoolSize()).isEqualTo(8);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.primary-storage.type=rocksdb",
        "camunda.data.primary-storage.rdbms.url=jdbc:postgresql://localhost:5432/camunda"
      })
  class WithRocksDbTypeAndRdbmsSet {
    final BrokerBasedProperties brokerCfg;

    WithRocksDbTypeAndRdbmsSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldNotEnableRdbmsEngineState() {
      assertThat(brokerCfg.getData().getRdbmsEngineState().isEnabled()).isFalse();
    }
  }

  @Nested
  class StartupValidation {

    @Test
    void shouldFailToStartWithRdbmsTypeButNoUrl() {
      // given
      final var app = newApp(Map.of("camunda.data.primary-storage.type", "rdbms"));

      // when/then
      assertThatThrownBy(app::run)
          .hasRootCauseInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("camunda.data.primary-storage.rdbms.url must be set");
    }

    @Test
    void shouldFailToStartWithNonPositivePoolSize() {
      // given
      final var app =
          newApp(
              Map.of(
                  "camunda.data.primary-storage.type",
                  "rdbms",
                  "camunda.data.primary-storage.rdbms.url",
                  "jdbc:postgresql://localhost:5432/camunda",
                  "camunda.data.primary-storage.rdbms.pool-size",
                  "0"));

      // when/then
      assertThatThrownBy(app::run)
          .hasRootCauseInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("camunda.data.primary-storage.rdbms.pool-size must be positive");
    }

    private SpringApplication newApp(final Map<String, Object> properties) {
      final var app =
          new SpringApplication(
              UnifiedConfiguration.class,
              BrokerBasedPropertiesOverride.class,
              UnifiedConfigurationHelper.class);
      app.setAdditionalProfiles("broker");
      app.setDefaultProperties(properties);
      return app;
    }
  }
}
