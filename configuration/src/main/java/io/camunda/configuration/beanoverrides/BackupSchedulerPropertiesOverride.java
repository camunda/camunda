/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.zeebe.broker.system.configuration.backup.BackupSchedulerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupSchedulerRetentionCfg;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("broker | gateway")
@DependsOn("unifiedConfigurationHelper")
public class BackupSchedulerPropertiesOverride {

  private final UnifiedConfiguration unifiedConfiguration;

  public BackupSchedulerPropertiesOverride(final UnifiedConfiguration unifiedConfiguration) {
    this.unifiedConfiguration = unifiedConfiguration;
  }

  @Bean
  @Primary
  public BackupSchedulerCfg backupSchedulerCfg() {
    final BackupSchedulerCfg backupSchedulerCfg = new BackupSchedulerCfg();

    populateFromBackup(unifiedConfiguration, backupSchedulerCfg);
    populateFromRetention(unifiedConfiguration, backupSchedulerCfg.getRetention());

    return backupSchedulerCfg;
  }

  private void populateFromBackup(
      final UnifiedConfiguration unifiedConfiguration,
      final BackupSchedulerCfg backupSchedulerCfg) {
    final var backupConfig =
        unifiedConfiguration.getCamunda().getData().getPrimaryStorage().getBackup();

    backupSchedulerCfg.setContinuous(backupConfig.isContinuous());
    backupSchedulerCfg.setRequired(backupConfig.isRequired());
    backupSchedulerCfg.setSchedule(backupConfig.getSchedule());
    backupSchedulerCfg.setOffset(backupConfig.getOffset());
    backupSchedulerCfg.setCheckpointInterval(backupConfig.getCheckpointInterval());
  }

  private void populateFromRetention(
      final UnifiedConfiguration unifiedConfiguration,
      final BackupSchedulerRetentionCfg retentionCfg) {

    final var retentionConfig =
        unifiedConfiguration.getCamunda().getData().getPrimaryStorage().getBackup().getRetention();

    retentionCfg.setWindow(retentionConfig.getWindow());
    retentionCfg.setCleanupSchedule(retentionConfig.getCleanupSchedule());
  }
}
