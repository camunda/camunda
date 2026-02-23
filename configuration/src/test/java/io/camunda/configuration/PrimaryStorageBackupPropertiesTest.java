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
}
