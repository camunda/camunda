/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.zeebe.backup.schedule.Schedule.CronSchedule;
import io.camunda.zeebe.backup.schedule.Schedule.IntervalSchedule;
import io.camunda.zeebe.backup.schedule.Schedule.NoneSchedule;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class PrimaryStorageBackupPropertiesTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
        "camunda.data.primary-storage.backup.schedule=0 0 * * * ?",
        "camunda.data.primary-storage.backup.retention.clean-up-schedule=0 30 * * * ?",
      })
  class CronExpressionScheduleConfiguration {
    final BackupCfg backupSchedulerCfg;

    CronExpressionScheduleConfiguration(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      backupSchedulerCfg = brokerBasedProperties.getData().getBackup();
    }

    @Test
    void shouldSetCronExpression() {
      assertThat(backupSchedulerCfg.getSchedule()).isInstanceOf(CronSchedule.class);

      assertThat(backupSchedulerCfg.getRetention().getCleanupSchedule())
          .isInstanceOf(CronSchedule.class);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
        "camunda.data.primary-storage.backup.schedule=PT5H",
        "camunda.data.primary-storage.backup.checkpoint-interval=PT2M",
        "camunda.data.primary-storage.backup.retention.clean-up-schedule=PT12H",
        "camunda.data.primary-storage.backup.retention.window=P7D"
      })
  class ISO8601ScheduleConfiguration {
    final BackupCfg backupSchedulerCfg;

    ISO8601ScheduleConfiguration(@Autowired final BrokerBasedProperties brokerCfg) {
      backupSchedulerCfg = brokerCfg.getData().getBackup();
    }

    @Test
    void shouldSetIsoDurations() {
      assertThat(backupSchedulerCfg.getSchedule()).isInstanceOf(IntervalSchedule.class);
      assertThat(backupSchedulerCfg.getCheckpointInterval()).isEqualTo(Duration.ofMinutes(2));
      assertThat(backupSchedulerCfg.getRetention().getCleanupSchedule())
          .isInstanceOf(IntervalSchedule.class);
      assertThat(backupSchedulerCfg.getRetention().getWindow()).isEqualTo(Duration.ofDays(7));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=none",
        "camunda.data.primary-storage.backup.schedule=PT5H",
        "camunda.data.primary-storage.backup.checkpoint-interval=PT2M",
        "camunda.data.primary-storage.backup.retention.clean-up-schedule=PT12H",
        "camunda.data.primary-storage.backup.retention.window=P7D",
        "camunda.data.primary-storage.backup.continuous=true",
        "camunda.data.primary-storage.backup.required=true",
        "camunda.data.primary-storage.backup.offset=100"
      })
  class CompleteSchedulerConfiguration {
    final BackupCfg backupSchedulerCfg;

    CompleteSchedulerConfiguration(@Autowired final BrokerBasedProperties brokerCfg) {
      backupSchedulerCfg = brokerCfg.getData().getBackup();
    }

    @Test
    void shouldParseConfig() {
      assertThat(backupSchedulerCfg.getSchedule()).isInstanceOf(IntervalSchedule.class);
      assertThat(backupSchedulerCfg.getCheckpointInterval()).isEqualTo(Duration.ofMinutes(2));
      assertThat(backupSchedulerCfg.getRetention().getCleanupSchedule())
          .isInstanceOf(IntervalSchedule.class);
      assertThat(backupSchedulerCfg.getRetention().getWindow()).isEqualTo(Duration.ofDays(7));
      assertThat(backupSchedulerCfg.isContinuous()).isTrue();
      assertThat(backupSchedulerCfg.isRequired()).isTrue();
      assertThat(backupSchedulerCfg.getOffset()).isEqualTo(100);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
        "camunda.data.primary-storage.backup.schedule=P5H",
        "camunda.data.primary-storage.backup.retention.cleanup-schedule=* * * t *",
      })
  class InvalidSchedulerConfiguration {
    final BackupCfg backupSchedulerCfg;

    InvalidSchedulerConfiguration(@Autowired final BrokerBasedProperties brokerCfg) {
      backupSchedulerCfg = brokerCfg.getData().getBackup();
    }

    @Test
    void shouldThrowIllegalArgumentException() {
      assertThatThrownBy(backupSchedulerCfg::getSchedule)
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> backupSchedulerCfg.getRetention().getCleanupSchedule())
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
        "camunda.data.primary-storage.backup.retention.clean-up-schedule=none",
      })
  class NoneRetentionSchedulerConfiguration {
    final BackupCfg backupSchedulerCfg;

    NoneRetentionSchedulerConfiguration(@Autowired final BrokerBasedProperties brokerCfg) {
      backupSchedulerCfg = brokerCfg.getData().getBackup();
    }

    @Test
    void shouldParseConfig() {
      assertThat(backupSchedulerCfg.getRetention().getCleanupSchedule())
          .isInstanceOf(NoneSchedule.class);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.primary-storage.backup.read-timeout=90s",
        "camunda.data.primary-storage.backup.write-timeout=120s"
      })
  class RequestTimeoutConfiguration {
    final BackupCfg backupCfg;

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
    final BackupCfg backupCfg;

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
      // given
      final Map<String, Object> properties =
          Map.of(
              "camunda.data.secondary-storage.type",
              "rdbms",
              "camunda.data.primary-storage.backup.read-timeout",
              "-10s");

      final var app =
          new SpringApplication(
              UnifiedConfiguration.class,
              BrokerBasedPropertiesOverride.class,
              UnifiedConfigurationHelper.class);
      app.setAdditionalProfiles("broker");
      app.setDefaultProperties(properties);

      // when/then - application startup should fail
      assertThatThrownBy(app::run)
          .hasRootCauseInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("BackupStore readTimeout must be positive");
    }

    @Test
    void shouldFailToStartWithNegativeWriteTimeout() {
      // given
      final Map<String, Object> properties =
          Map.of(
              "camunda.data.secondary-storage.type",
              "rdbms",
              "camunda.data.primary-storage.backup.write-timeout",
              "-10s");

      final var app =
          new SpringApplication(
              UnifiedConfiguration.class,
              BrokerBasedPropertiesOverride.class,
              UnifiedConfigurationHelper.class);
      app.setAdditionalProfiles("broker");
      app.setDefaultProperties(properties);

      // when/then - application startup should fail
      assertThatThrownBy(app::run)
          .hasRootCauseInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("BackupStore writeTimeout must be positive");
    }

    @Test
    void shouldFailToStartWithZeroReadTimeout() {
      // given
      final Map<String, Object> properties =
          Map.of(
              "camunda.data.secondary-storage.type",
              "rdbms",
              "camunda.data.primary-storage.backup.read-timeout",
              "0s");

      final var app =
          new SpringApplication(
              UnifiedConfiguration.class,
              BrokerBasedPropertiesOverride.class,
              UnifiedConfigurationHelper.class);
      app.setAdditionalProfiles("broker");
      app.setDefaultProperties(properties);

      // when/then - application startup should fail
      assertThatThrownBy(app::run)
          .hasRootCauseInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("BackupStore readTimeout must be positive");
    }

    @Test
    void shouldFailToStartWithZeroWriteTimeout() {
      // given
      final Map<String, Object> properties =
          Map.of(
              "camunda.data.secondary-storage.type",
              "rdbms",
              "camunda.data.primary-storage.backup.write-timeout",
              "0s");

      final var app =
          new SpringApplication(
              UnifiedConfiguration.class,
              BrokerBasedPropertiesOverride.class,
              UnifiedConfigurationHelper.class);
      app.setAdditionalProfiles("broker");
      app.setDefaultProperties(properties);

      // when/then - application startup should fail
      assertThatThrownBy(app::run)
          .hasRootCauseInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("BackupStore writeTimeout must be positive");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
        "camunda.data.primary-storage.backup.continuous=true",
        "zeebe.broker.experimental.continuousBackups=true"
      })
  class ContinuousPropertyCompatibility {
    final BackupCfg backupSchedulerCfg;

    ContinuousPropertyCompatibility(@Autowired final BrokerBasedProperties brokerCfg) {
      backupSchedulerCfg = brokerCfg.getData().getBackup();
    }

    @Test
    void shouldParseConfig() {
      assertThat(backupSchedulerCfg.isContinuous()).isTrue();
    }
  }

  @Nested
  class ContinuousBackupsStartupCompatibility {

    @Test
    void shouldFailToStartWithElasticsearchAndContinuousBackups() {

      // given
      final Map<String, Object> properties =
          Map.of(
              "camunda.data.primary-storage.backup.continuous",
              "true",
              "camunda.data.primary-storage.backup.schedule",
              "PT1H",
              "camunda.data.secondary-storage.type",
              "elasticsearch");

      final var app =
          new SpringApplication(
              UnifiedConfiguration.class,
              BrokerBasedPropertiesOverride.class,
              UnifiedConfigurationHelper.class);
      app.setAdditionalProfiles("broker");
      app.setDefaultProperties(properties);

      // when/then - application startup should fail
      assertThatThrownBy(app::run)
          .hasRootCauseInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "Continuous backups are not compatible with secondary storage: `elasticsearch`. Please disable continuous backups");
    }

    @Test
    void shouldFailToStartWithOpensearchAndContinuousBackups() {

      // given
      final Map<String, Object> properties =
          Map.of(
              "camunda.data.primary-storage.backup.continuous",
              "true",
              "camunda.data.primary-storage.backup.schedule",
              "PT1H",
              "camunda.data.secondary-storage.type",
              "opensearch");

      final var app =
          new SpringApplication(
              UnifiedConfiguration.class,
              BrokerBasedPropertiesOverride.class,
              UnifiedConfigurationHelper.class);
      app.setAdditionalProfiles("broker");
      app.setDefaultProperties(properties);

      // when/then - application startup should fail
      assertThatThrownBy(app::run)
          .hasRootCauseInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "Continuous backups are not compatible with secondary storage: `opensearch`. Please disable continuous backups");
    }

    @Test
    void shouldNotStartIfScheduleIsProvided() {

      // given
      final Map<String, Object> properties =
          Map.of(
              "camunda.data.primary-storage.backup.schedule",
              "PT1H",
              "camunda.data.secondary-storage.type",
              "opensearch");

      final var app =
          new SpringApplication(
              UnifiedConfiguration.class,
              BrokerBasedPropertiesOverride.class,
              UnifiedConfigurationHelper.class);
      app.setAdditionalProfiles("broker");
      app.setDefaultProperties(properties);

      // when/then - application startup should succeed
      assertThatThrownBy(app::run)
          .hasRootCauseInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "Continuous backups are not compatible with secondary storage: `opensearch`. Please disable continuous backups");
    }

    @Test
    void shouldStartOnNoneSchedule() {

      // given
      final Map<String, Object> properties =
          Map.of(
              "camunda.data.primary-storage.backup.schedule",
              "none",
              "camunda.data.secondary-storage.type",
              "opensearch");

      final var app =
          new SpringApplication(
              UnifiedConfiguration.class,
              BrokerBasedPropertiesOverride.class,
              UnifiedConfigurationHelper.class);
      app.setAdditionalProfiles("broker");
      app.setDefaultProperties(properties);

      // when/then - application startup should succeed
      assertThatCode(app::run).doesNotThrowAnyException();
    }

    @Test
    void shouldStartIfContinuousBackupsEnabled() {

      // given
      final Map<String, Object> properties =
          Map.of(
              "camunda.data.primary-storage.backup.continuous",
              "true",
              "camunda.data.secondary-storage.type",
              "opensearch");

      final var app =
          new SpringApplication(
              UnifiedConfiguration.class,
              BrokerBasedPropertiesOverride.class,
              UnifiedConfigurationHelper.class);
      app.setAdditionalProfiles("broker");
      app.setDefaultProperties(properties);

      // when/then - application startup should succeed
      assertThatThrownBy(app::run)
          .hasRootCauseInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(
              "Continuous backups are not compatible with secondary storage: `opensearch`. Please disable continuous backups");
    }

    @Test
    void shouldStartOnRdbms() {

      // given
      final Map<String, Object> properties =
          Map.of(
              "camunda.data.primary-storage.backup.continuous",
              "true",
              "camunda.data.primary-storage.backup.schedule",
              "PT1H",
              "camunda.data.secondary-storage.type",
              "rdbms");

      final var app =
          new SpringApplication(
              UnifiedConfiguration.class,
              BrokerBasedPropertiesOverride.class,
              UnifiedConfigurationHelper.class);
      app.setAdditionalProfiles("broker");
      app.setDefaultProperties(properties);

      // when/then - application startup should succeed
      assertThatCode(app::run).doesNotThrowAnyException();
    }

    @Test
    void shouldStartOnNoDb() {

      // given
      final Map<String, Object> properties =
          Map.of(
              "camunda.data.primary-storage.backup.continuous",
              "true",
              "camunda.data.primary-storage.backup.schedule",
              "PT1H",
              "camunda.data.secondary-storage.type",
              "none");

      final var app =
          new SpringApplication(
              UnifiedConfiguration.class,
              BrokerBasedPropertiesOverride.class,
              UnifiedConfigurationHelper.class);
      app.setAdditionalProfiles("broker");
      app.setDefaultProperties(properties);

      // when/then - application startup should succeed
      assertThatCode(app::run).doesNotThrowAnyException();
    }
  }

  @Nested
  class BackupLocationIsolation {

    @Test
    void shouldRejectPhysicalTenantsResolvingToOneBackupLocation() {
      // given a tenant that inherits the root backup store because it does not override it
      final var environment = new MockEnvironment();
      environment
          .getPropertySources()
          .addFirst(
              new MapPropertySource(
                  "test",
                  Map.of(
                      "camunda.data.primary-storage.backup.store", "FILESYSTEM",
                      "camunda.data.primary-storage.backup.filesystem.base-path", "/backups",
                      "camunda.physical-tenants.tenanta.data.secondary-storage.elasticsearch.index-prefix",
                          "tenanta",
                      "camunda.physical-tenants.tenanta.security.initialization.default-roles.admin.users[0]",
                          "tenanta-admin")));
      UnifiedConfigurationHelper.setCustomEnvironment(environment);

      final var camunda = new Camunda();
      Binder.get(environment).bind(Camunda.PREFIX, Bindable.ofInstance(camunda));

      // when / then
      try {
        assertThatThrownBy(() -> PhysicalTenantResolver.of(environment, camunda))
            .isInstanceOf(UnifiedConfigurationException.class)
            .hasMessageContaining("must not share a primary-storage backup location")
            .hasMessageContaining("tenanta");
      } finally {
        UnifiedConfigurationHelper.setCustomEnvironment(null);
      }
    }
  }
}
