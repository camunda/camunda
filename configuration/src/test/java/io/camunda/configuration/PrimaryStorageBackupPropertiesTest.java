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
import io.camunda.zeebe.broker.system.configuration.backup.BackupSchedulerCfg;
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
public class PrimaryStorageBackupPropertiesTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.primary-storage.backup.schedule=0 0 * * * ?",
        "camunda.data.primary-storage.backup.retention.clean-up-schedule=0 30 * * * ?",
      })
  class CronExpressionScheduleConfiguration {
    final BackupSchedulerCfg backupSchedulerCfg;

    CronExpressionScheduleConfiguration(
        @Autowired final BrokerBasedProperties brokerBasedProperties) {
      backupSchedulerCfg = brokerBasedProperties.getData().getBackupScheduler();
    }

    @Test
    void shouldSetCronExpression() {
      assertThat(backupSchedulerCfg.getSchedule()).isEqualTo("0 0 * * * ?");
      assertThat(backupSchedulerCfg.getRetention().getCleanupSchedule()).isEqualTo("0 30 * * * ?");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.primary-storage.backup.schedule=PT5H",
        "camunda.data.primary-storage.backup.checkpoint-interval=PT2M",
        "camunda.data.primary-storage.backup.retention.clean-up-schedule=PT12H",
        "camunda.data.primary-storage.backup.retention.window=P7D"
      })
  class ISO8601ScheduleConfiguration {
    final BackupSchedulerCfg backupSchedulerCfg;

    ISO8601ScheduleConfiguration(@Autowired final BrokerBasedProperties brokerCfg) {
      backupSchedulerCfg = brokerCfg.getData().getBackupScheduler();
    }

    @Test
    void shouldSetIsoDurations() {
      assertThat(backupSchedulerCfg.getSchedule()).isEqualTo("PT5H");
      assertThat(backupSchedulerCfg.getCheckpointInterval()).isEqualTo(Duration.ofMinutes(2));
      assertThat(backupSchedulerCfg.getRetention().getCleanupSchedule()).isEqualTo("PT12H");
      assertThat(backupSchedulerCfg.getRetention().getWindow()).isEqualTo(Duration.ofDays(7));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.primary-storage.backup.schedule=PT5H",
        "camunda.data.primary-storage.backup.checkpoint-interval=PT2M",
        "camunda.data.primary-storage.backup.retention.clean-up-schedule=PT12H",
        "camunda.data.primary-storage.backup.retention.window=P7D",
        "camunda.data.primary-storage.backup.continuous=true",
        "camunda.data.primary-storage.backup.required=true",
        "camunda.data.primary-storage.backup.offset=100"
      })
  class CompleteSchedulerConfiguration {
    final BackupSchedulerCfg backupSchedulerCfg;

    CompleteSchedulerConfiguration(@Autowired final BrokerBasedProperties brokerCfg) {
      backupSchedulerCfg = brokerCfg.getData().getBackupScheduler();
    }

    @Test
    void shouldSetCronExpression() {
      assertThat(backupSchedulerCfg.getSchedule()).isEqualTo("PT5H");
      assertThat(backupSchedulerCfg.getCheckpointInterval()).isEqualTo(Duration.ofMinutes(2));
      assertThat(backupSchedulerCfg.getRetention().getCleanupSchedule()).isEqualTo("PT12H");
      assertThat(backupSchedulerCfg.getRetention().getWindow()).isEqualTo(Duration.ofDays(7));
      assertThat(backupSchedulerCfg.isContinuous()).isTrue();
      assertThat(backupSchedulerCfg.isRequired()).isTrue();
      assertThat(backupSchedulerCfg.getOffset()).isEqualTo(100);
    }
  }
}
