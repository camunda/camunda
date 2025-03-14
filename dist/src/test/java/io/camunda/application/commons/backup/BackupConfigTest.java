/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.camunda.operate.property.OperateProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
public class BackupConfigTest {

  OperateProperties operateProperties;
  TasklistProperties tasklistProperties;

  @BeforeEach
  public void setup() {
    operateProperties = new OperateProperties();
    tasklistProperties = new TasklistProperties();
  }

  @Test
  public void shouldUseOperateIfOnlyAvailable() {
    operateProperties.getBackup().setRepositoryName("repo-1");
    checkRepo(operateProperties, null);
    checkRepo(operateProperties, tasklistProperties);
  }

  @Test
  public void shouldUseOperateIfBothAvailable() {
    operateProperties.getBackup().setRepositoryName("repo-1").setSnapshotTimeout(17);
    tasklistProperties.getBackup().setRepositoryName("repo-1");
    final var props = checkRepo(operateProperties, tasklistProperties);
    assertThat(props.snapshotTimeout()).isEqualTo(17);
  }

  @Test
  public void shouldThrowIfRepositoryNamesAreDifferent() {
    operateProperties.getBackup().setRepositoryName("repo-1").setSnapshotTimeout(17);
    tasklistProperties.getBackup().setRepositoryName("repo-2");
    assertThatThrownBy(
            () ->
                new BackupConfig(operateProperties, tasklistProperties).backupRepositoryProps(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(BackupConfig.differentRepoNameFormat.formatted(""))
        .hasMessageContaining("operate=Optional[repo-1]")
        .hasMessageContaining("tasklist=Optional[repo-2]");
  }

  @Test
  public void shouldUseTasklistIfOnlyAvailable() {
    tasklistProperties.getBackup().setRepositoryName("repo-1");
    checkRepo(null, tasklistProperties);
    checkRepo(operateProperties, tasklistProperties);
  }

  private BackupRepositoryProps checkRepo(
      final OperateProperties operateProperties, final TasklistProperties tasklistProperties) {
    return checkRepo(operateProperties, tasklistProperties, null);
  }

  private BackupRepositoryProps checkRepo(
      final OperateProperties operateProperties,
      final TasklistProperties tasklistProperties,
      final Environment environment) {
    final var config = new BackupConfig(operateProperties, tasklistProperties);
    final var props = config.backupRepositoryProps(environment);
    assertThat(props.repositoryName()).isEqualTo("repo-1");
    return props;
  }

  @Test
  public void shouldReturnEmptyInstanceIfNoConfiguration() {
    assertThat(new BackupConfig(null, null).backupRepositoryProps(null))
        .isEqualTo(BackupRepositoryProps.EMPTY);
  }
}
