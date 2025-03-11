/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.backup;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CAMUNDA_OPTIMIZE_DATABASE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.config.operate.OperateProperties;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.db.DatabaseBackup;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
public class BackupConfigTest {

  OperateProperties operateProperties;
  TasklistProperties tasklistProperties;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  ConfigurationService configurationService;

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
                new BackupConfig(operateProperties, tasklistProperties, null)
                    .backupRepositoryProps(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(BackupConfig.differentRepoNameFormat.formatted(""))
        .hasMessageContaining("operate=Optional[repo-1]")
        .hasMessageContaining("tasklist=Optional[repo-2]")
        .hasMessageContaining("optimize=Optional.empty");
  }

  @Test
  public void shouldUseTasklistIfOnlyAvailable() {
    tasklistProperties.getBackup().setRepositoryName("repo-1");
    checkRepo(null, tasklistProperties);
    checkRepo(operateProperties, tasklistProperties);
  }

  @Test
  public void shouldUseOptimizeIfOnlyAvailable() {
    final var backup = new DatabaseBackup();
    backup.setSnapshotRepositoryName("repo-1");
    when(configurationService.getElasticSearchConfiguration().getBackup()).thenReturn(backup);
    final var environment = mock(Environment.class);
    when(environment.getProperty(eq(CAMUNDA_OPTIMIZE_DATABASE), (String) any()))
        .thenReturn(DatabaseType.ELASTICSEARCH.toString());
    checkRepo(null, null, environment);
  }

  private BackupRepositoryProps checkRepo(
      final OperateProperties operateProperties, final TasklistProperties tasklistProperties) {
    return checkRepo(operateProperties, tasklistProperties, null);
  }

  private BackupRepositoryProps checkRepo(
      final OperateProperties operateProperties,
      final TasklistProperties tasklistProperties,
      final Environment environment) {
    final var config =
        new BackupConfig(operateProperties, tasklistProperties, configurationService);
    final var props = config.backupRepositoryProps(environment);
    assertThat(props.repositoryName()).isEqualTo("repo-1");
    return props;
  }

  @Test
  public void shouldReturnEmptyInstanceIfNoConfiguration() {
    assertThat(new BackupConfig(null, null, null).backupRepositoryProps(null))
        .isEqualTo(BackupRepositoryProps.EMPTY);
  }
}
