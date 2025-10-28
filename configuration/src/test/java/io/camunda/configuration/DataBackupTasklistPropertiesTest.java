/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.TasklistPropertiesOverride;
import io.camunda.tasklist.property.TasklistProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  TasklistPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
public class DataBackupTasklistPropertiesTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.backup.repository-name=repositoryNameNew",
      })
  class WithOnlyUnifiedConfigSet {
    final TasklistProperties tasklistProperties;

    WithOnlyUnifiedConfigSet(@Autowired final TasklistProperties tasklistProperties) {
      this.tasklistProperties = tasklistProperties;
    }

    @Test
    void shouldSetRepositoryName() {
      assertThat(tasklistProperties.getBackup().getRepositoryName()).isEqualTo("repositoryNameNew");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.operate.backup.repositoryName=repositoryNameOperate",
      })
  class WithOnlyOperateLegacySet {
    final TasklistProperties tasklistProperties;

    WithOnlyOperateLegacySet(@Autowired final TasklistProperties tasklistProperties) {
      this.tasklistProperties = tasklistProperties;
    }

    @Test
    void shouldSetRepositoryNameFromOperate() {
      assertThat(tasklistProperties.getBackup().getRepositoryName())
          .isEqualTo("repositoryNameOperate");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.tasklist.backup.repositoryName=repositoryNameTasklist",
      })
  class WithOnlyTasklistLegacySet {
    final TasklistProperties tasklistProperties;

    WithOnlyTasklistLegacySet(@Autowired final TasklistProperties tasklistProperties) {
      this.tasklistProperties = tasklistProperties;
    }

    @Test
    void shouldSetRepositoryFromTasklist() {
      assertThat(tasklistProperties.getBackup().getRepositoryName())
          .isEqualTo("repositoryNameTasklist");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.operate.backup.repositoryName=repositoryName",
        "camunda.tasklist.backup.repositoryName=repositoryName",
      })
  class WithOperateAndTasklistLegacySet {
    final TasklistProperties tasklistProperties;

    WithOperateAndTasklistLegacySet(@Autowired final TasklistProperties tasklistProperties) {
      this.tasklistProperties = tasklistProperties;
    }

    @Test
    void shouldSetRepositoryNameIfLegacyMatch() {
      assertThat(tasklistProperties.getBackup().getRepositoryName()).isEqualTo("repositoryName");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.data.backup.repository-name=repositoryName",
        // legacy configuration operate
        "camunda.operate.backup.repositoryName=repositoryName",
        // legacy configuration tasklist
        "camunda.tasklist.backup.repositoryName=repositoryName",
      })
  class WithNewAndLegacySet {
    final TasklistProperties tasklistProperties;

    WithNewAndLegacySet(@Autowired final TasklistProperties tasklistProperties) {
      this.tasklistProperties = tasklistProperties;
    }

    @Test
    void shouldSetRepositoryNameIfNewAndLegacyMatch() {
      assertThat(tasklistProperties.getBackup().getRepositoryName()).isEqualTo("repositoryName");
    }
  }
}
