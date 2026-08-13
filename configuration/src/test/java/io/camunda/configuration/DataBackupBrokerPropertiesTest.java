/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class DataBackupBrokerPropertiesTest {
  @Nested
  @TestPropertySource(properties = {"camunda.data.backup.store=azure"})
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetStore() {
      assertThat(brokerCfg.getData().getBackup().getStore()).isEqualTo(BackupStoreType.AZURE);
    }
  }

  @Nested
  @TestPropertySource(properties = {"zeebe.broker.data.backup.store=azure"})
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetStore() {
      assertThat(brokerCfg.getData().getBackup().getStore()).isEqualTo(BackupStoreType.AZURE);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.data.backup.store=azure",
        // legacy
        "zeebe.broker.data.backup.store=azure",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetStoreFromNew() {
      assertThat(brokerCfg.getData().getBackup().getStore()).isEqualTo(BackupStoreType.AZURE);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.backup.read-timeout=90s",
        "camunda.data.backup.write-timeout=120s"
      })
  class RequestTimeoutConfiguration {
    final BackupStoreCfg backupCfg;

    RequestTimeoutConfiguration(@Autowired final BrokerBasedProperties brokerCfg) {
      backupCfg = brokerCfg.getData().getBackup();
    }

    @Test
    void shouldSetReadAndWriteTimeoutSeparately() {
      assertThat(backupCfg.getReadTimeout()).isEqualTo(Duration.ofSeconds(90));
      assertThat(backupCfg.getWriteTimeout()).isEqualTo(Duration.ofSeconds(120));
    }
  }

  @Nested
  class DefaultRequestTimeoutConfiguration {
    final BackupStoreCfg backupCfg;

    DefaultRequestTimeoutConfiguration(@Autowired final BrokerBasedProperties brokerCfg) {
      backupCfg = brokerCfg.getData().getBackup();
    }

    @Test
    void shouldLeaveTimeoutsUnsetSoThatStoreDefaultsApply() {
      assertThat(backupCfg.getReadTimeout()).isNull();
      assertThat(backupCfg.getWriteTimeout()).isNull();
    }
  }

  @Nested
  class RequestTimeoutStartupValidation {

    @Test
    void shouldFailToStartWithNegativeReadTimeout() {
      // given/when/then - application startup should fail
      assertThatStartupFails("camunda.data.backup.read-timeout", "-10s")
          .hasMessageContaining("BackupStore readTimeout must be positive");
    }

    @Test
    void shouldFailToStartWithNegativeWriteTimeout() {
      // given/when/then - application startup should fail
      assertThatStartupFails("camunda.data.backup.write-timeout", "-10s")
          .hasMessageContaining("BackupStore writeTimeout must be positive");
    }

    @Test
    void shouldFailToStartWithZeroReadTimeout() {
      // given/when/then - application startup should fail
      assertThatStartupFails("camunda.data.backup.read-timeout", "0s")
          .hasMessageContaining("BackupStore readTimeout must be positive");
    }

    @Test
    void shouldFailToStartWithZeroWriteTimeout() {
      // given/when/then - application startup should fail
      assertThatStartupFails("camunda.data.backup.write-timeout", "0s")
          .hasMessageContaining("BackupStore writeTimeout must be positive");
    }

    private org.assertj.core.api.AbstractThrowableAssert<?, ? extends Throwable>
        assertThatStartupFails(final String property, final String value) {
      final Map<String, Object> properties =
          Map.of("camunda.data.secondary-storage.type", "rdbms", property, value);

      final var app =
          new SpringApplication(
              UnifiedConfiguration.class,
              BrokerBasedPropertiesOverride.class,
              UnifiedConfigurationHelper.class);
      app.setAdditionalProfiles("broker");
      app.setDefaultProperties(properties);
      // the test classpath contains spring-boot-starter-web but no web server factory
      app.setWebApplicationType(WebApplicationType.NONE);

      return assertThatThrownBy(app::run).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
  }
}
