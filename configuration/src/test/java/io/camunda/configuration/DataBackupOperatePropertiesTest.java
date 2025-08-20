/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.OperatePropertiesOverride;
import io.camunda.operate.property.OperateProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  OperatePropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
public class DataBackupOperatePropertiesTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.backup.repository-name=repositoryNameNew",
        "camunda.data.backup.snapshot-timeout=5",
        "camunda.data.backup.incomplete-check-timeout=3m",
      })
  class WithOnlyUnifiedConfigSet {
    final OperateProperties operateProperties;

    WithOnlyUnifiedConfigSet(@Autowired final OperateProperties operateProperties) {
      this.operateProperties = operateProperties;
    }

    @Test
    void shouldSetRepositoryName() {
      assertThat(operateProperties.getBackup().getRepositoryName()).isEqualTo("repositoryNameNew");
    }

    @Test
    void shouldSetSnapshotTimeout() {
      assertThat(operateProperties.getBackup().getSnapshotTimeout()).isEqualTo(5);
    }

    @Test
    void shouldSetIncompleteCheckTimeout() {
      assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds()).isEqualTo(180);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.tasklist.backup.repositoryName=repositoryNameLegacyTasklist",
      })
  class WithOnlyTasklistLegacySet {
    final OperateProperties operateProperties;

    WithOnlyTasklistLegacySet(@Autowired final OperateProperties operateProperties) {
      this.operateProperties = operateProperties;
    }

    @Test
    void shouldNotSetRepositoryNameFromLegacyTasklist() {
      assertThat(operateProperties.getBackup().getRepositoryName()).isNull();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.operate.backup.repositoryName=repositoryNameLegacyOperate",
        "camunda.operate.backup.snapshotTimeout=5",
        "camunda.operate.backup.incompleteCheckTimeoutInSeconds=180",
      })
  class WithOnlyOperateLegacySet {
    final OperateProperties operateProperties;

    WithOnlyOperateLegacySet(@Autowired final OperateProperties operateProperties) {
      this.operateProperties = operateProperties;
    }

    @Test
    void shouldSetRepositoryNameFromLegacyOperate() {
      assertThat(operateProperties.getBackup().getRepositoryName())
          .isEqualTo("repositoryNameLegacyOperate");
    }

    @Test
    void shouldSetSnapshotTimeoutFromLegacyOperate() {
      assertThat(operateProperties.getBackup().getSnapshotTimeout()).isEqualTo(5);
    }

    @Test
    void shouldSetIncompleteCheckTimeoutFromLegacyOperate() {
      assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds()).isEqualTo(180);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.data.backup.repository-name=repositoryNameNew",
        "camunda.data.backup.snapshot-timeout=5",
        "camunda.data.backup.incomplete-check-timeout=3m",
        // legacy configuration tasklist
        "camunda.tasklist.backup.repositoryName=repositoryNameLegacyTasklist",
        // legacy configuration operate
        "camunda.operate.backup.repositoryName=repositoryNameLegacyOperate",
        "camunda.operate.backup.snapshotTimeout=2",
        "camunda.operate.backup.incompleteCheckTimeoutInSeconds=90",
      })
  class WithNewAndLegacySet {
    final OperateProperties operateProperties;

    WithNewAndLegacySet(@Autowired final OperateProperties operateProperties) {
      this.operateProperties = operateProperties;
    }

    @Test
    void shouldSetRepositoryNameFromNew() {
      assertThat(operateProperties.getBackup().getRepositoryName()).isEqualTo("repositoryNameNew");
    }

    @Test
    void shouldSetSnapshotTimeoutFromNew() {
      assertThat(operateProperties.getBackup().getSnapshotTimeout()).isEqualTo(5);
    }

    @Test
    void shouldSetIncompleteCheckTimeoutFromNew() {
      assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds()).isEqualTo(180);
    }
  }
}
