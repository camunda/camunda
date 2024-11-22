/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.config.backup;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BackupConfig {

  @Bean
  public BackupRepositoryProps backupRepositoryProps(final TasklistProperties tasklistProperties) {
    final var backup = tasklistProperties.getBackup();
    return new BackupRepositoryProps() {
      @Override
      public String version() {
        return tasklistProperties.getVersion();
      }

      @Override
      public String repositoryName() {
        return backup.getRepositoryName();
      }

      @Override
      public boolean includeGlobalState() {
        return true;
      }
    };
  }
}
