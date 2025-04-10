/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import static io.camunda.application.commons.backup.ConfigValidation.*;

import io.camunda.application.commons.conditions.WebappEnabledCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.BackupRepositoryPropsRecord;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@Conditional(WebappEnabledCondition.class)
public class BackupConfig {

  public static String differentRepoNameFormat =
      "Expected the same repository in operate and tasklist backup config: given backup repositories are %s";

  final OperateProperties operateProperties;
  final TasklistProperties tasklistProperties;

  public BackupConfig(
      @Autowired(required = false) final OperateProperties operateProperties,
      @Autowired(required = false) final TasklistProperties tasklistProperties) {
    this.operateProperties = operateProperties;
    this.tasklistProperties = tasklistProperties;
  }

  @Bean
  public BackupRepositoryProps backupRepositoryProps(final Environment environment) {
    final var operateBackup =
        Optional.ofNullable(operateProperties).map(c -> props(c.getVersion(), c.getBackup()));
    final var tasklistBackup =
        Optional.ofNullable(tasklistProperties).map(c -> props(c.getVersion(), c.getBackup()));

    // A LinkedHashMap has to be used because it keeps insertion order
    // the first entry of the map will be used if all entries are present
    final var propMap = new LinkedHashMap<String, Optional<BackupRepositoryProps>>();
    propMap.put("operate", operateBackup);
    propMap.put("tasklist", tasklistBackup);

    final var props =
        allMatchHaving(
            Optional::empty,
            m -> String.format(differentRepoNameFormat, m),
            propMap,
            opt -> opt.map(BackupRepositoryProps::repositoryName),
            skipEmptyOptional());
    return props.orElse(BackupRepositoryProps.EMPTY);
  }

  @Bean("backupThreadPoolExecutor")
  public ThreadPoolTaskExecutor getTaskExecutor() {
    final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(8);
    executor.setKeepAliveSeconds(60);
    executor.setThreadNamePrefix("webapps_backup_");
    executor.setStrictEarlyShutdown(true);
    executor.setQueueCapacity(4096);
    executor.initialize();
    return executor;
  }

  public static BackupRepositoryProps props(
      final String tasklistVersion,
      final io.camunda.tasklist.property.BackupProperties operateProperties) {
    return new BackupRepositoryPropsRecord(tasklistVersion, operateProperties.getRepositoryName());
  }

  public static BackupRepositoryProps props(
      final String operateVersion,
      final io.camunda.operate.property.BackupProperties operateProperties) {
    return new BackupRepositoryPropsRecord(
        operateVersion,
        operateProperties.getRepositoryName(),
        operateProperties.getSnapshotTimeout(),
        operateProperties.getIncompleteCheckTimeoutInSeconds());
  }
}
