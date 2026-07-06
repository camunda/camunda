/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import static io.camunda.application.commons.backup.ConfigValidation.*;

import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.backup.repository.BackupRepositoryPropsRecord;
import io.camunda.webapps.profiles.ProfileWebApp;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@ConditionalOnBackupWebappsEnabled
public class BackupConfig {

  private static final Logger LOG = LoggerFactory.getLogger(BackupConfig.class);

  public static String differentRepoNameFormat =
      "Expected the same repository in operate and tasklist backup config: given backup repositories are %s";

  @Bean
  @ProfileWebApp
  public BackupRepositoryProps backupRepositoryProps(
      @Autowired(required = false) final OperateProperties operateProperties,
      @Autowired(required = false) final TasklistProperties tasklistProperties) {
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
    final var result = props.orElse(BackupRepositoryProps.EMPTY);
    final String repositoryName = result.repositoryName();
    if (repositoryName == null || repositoryName.isBlank()) {
      LOG.warn(
          "No backup repository configured. Backup endpoints are active but will reject all"
              + " requests until a repository is configured via"
              + " 'camunda.operate.backup.repositoryName' or"
              + " 'camunda.tasklist.backup.repositoryName'.");
    }
    return result;
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
