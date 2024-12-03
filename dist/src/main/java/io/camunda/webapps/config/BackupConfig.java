/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.config;

import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BackupConfig {

  public static String differentRepoNameFormat =
      "Expected the same repository in operate and tasklist backup config: given backup repositories are operate={%s}, tasklist={%s}";

  final OperateProperties operateProperties;
  final TasklistProperties tasklistProperties;

  public BackupConfig(
      @Autowired(required = false) final OperateProperties operateProperties,
      @Autowired(required = false) final TasklistProperties tasklistProperties) {
    this.operateProperties = operateProperties;
    this.tasklistProperties = tasklistProperties;
  }

  @Bean
  public BackupRepositoryProps backupRepositoryProps() {
    if (operateProperties != null && tasklistProperties != null) {
      // both are available, check that repo is the same:
      final var operateBackup = operateProperties.getBackup();
      final var tasklistBackup = tasklistProperties.getBackup();
      if (validRepoName(operateBackup.getRepositoryName())
          && validRepoName(tasklistBackup.getRepositoryName())
          && !Objects.equals(
              operateBackup.getRepositoryName(), tasklistBackup.getRepositoryName())) {
        throw new IllegalArgumentException(
            String.format(
                differentRepoNameFormat,
                operateBackup.getRepositoryName(),
                tasklistBackup.getRepositoryName()));
      } else if (validRepoName(operateBackup.getRepositoryName())) {
        return props(operateProperties.getVersion(), operateBackup);
      } else {
        return props(tasklistProperties.getVersion(), tasklistBackup);
      }
    } else if (operateProperties != null) {
      return props(operateProperties.getVersion(), operateProperties.getBackup());
    } else if (tasklistProperties != null) {
      return props(tasklistProperties.getVersion(), tasklistProperties.getBackup());
    } else {
      return BackupRepositoryProps.EMPTY;
    }
  }

  public static BackupRepositoryProps props(
      final String tasklistVersion,
      final io.camunda.tasklist.property.BackupProperties operateProperties) {
    return new BackupRepositoryProps() {
      @Override
      public String version() {
        return tasklistVersion;
      }

      @Override
      public String repositoryName() {
        return operateProperties.getRepositoryName();
      }
    };
  }

  public static BackupRepositoryProps props(
      final String operateVersion,
      final io.camunda.operate.property.BackupProperties operateProperties) {
    return new BackupRepositoryProps() {
      @Override
      public String version() {
        return operateVersion;
      }

      @Override
      public String repositoryName() {
        return operateProperties.getRepositoryName();
      }

      @Override
      public int snapshotTimeout() {
        return operateProperties.getSnapshotTimeout();
      }

      @Override
      public Long incompleteCheckTimeoutInSeconds() {
        return operateProperties.getIncompleteCheckTimeoutInSeconds();
      }
    };
  }

  private boolean validRepoName(final String s) {
    return s != null && !s.isEmpty();
  }
}
