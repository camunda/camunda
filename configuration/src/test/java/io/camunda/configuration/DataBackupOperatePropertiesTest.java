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
  @TestPropertySource(properties = {"camunda.data.secondary-storage.type=opensearch"})
  class Opensearch {
    @Nested
    @TestPropertySource(
        properties = {
          "camunda.data.secondary-storage.opensearch.backup.repository-name=repositoryNameNew",
          "camunda.data.secondary-storage.opensearch.backup.snapshot-timeout=5",
          "camunda.data.secondary-storage.opensearch.backup.incomplete-check-timeout=3m",
        })
    class WithOnlyUnifiedConfigSet {
      final OperateProperties operateProperties;

      WithOnlyUnifiedConfigSet(@Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetRepositoryName() {
        assertThat(operateProperties.getBackup().getRepositoryName())
            .isEqualTo("repositoryNameNew");
      }

      @Test
      void shouldSetSnapshotTimeout() {
        assertThat(operateProperties.getBackup().getSnapshotTimeout()).isEqualTo(5);
      }

      @Test
      void shouldSetIncompleteCheckTimeout() {
        assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
            .isEqualTo(180);
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          "camunda.data.backup.repository-name=repositoryNameNew",
          "camunda.data.backup.snapshot-timeout=5",
          "camunda.data.backup.incomplete-check-timeout=3m",
        })
    class WithOnlyOldUnifiedConfigSet {
      final OperateProperties operateProperties;

      WithOnlyOldUnifiedConfigSet(@Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetRepositoryName() {
        assertThat(operateProperties.getBackup().getRepositoryName())
            .isEqualTo("repositoryNameNew");
      }

      @Test
      void shouldSetSnapshotTimeout() {
        assertThat(operateProperties.getBackup().getSnapshotTimeout()).isEqualTo(5);
      }

      @Test
      void shouldSetIncompleteCheckTimeout() {
        assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
            .isEqualTo(180);
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          "camunda.tasklist.backup.repositoryName=repositoryNameTasklist",
        })
    class WithOnlyTasklistLegacySet {
      final OperateProperties operateProperties;

      WithOnlyTasklistLegacySet(@Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetRepositoryNameFromTasklist() {
        assertThat(operateProperties.getBackup().getRepositoryName())
            .isEqualTo("repositoryNameTasklist");
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          "camunda.operate.backup.repositoryName=repositoryNameOperate",
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
            .isEqualTo("repositoryNameOperate");
      }

      @Test
      void shouldSetSnapshotTimeoutFromLegacyOperate() {
        assertThat(operateProperties.getBackup().getSnapshotTimeout()).isEqualTo(5);
      }

      @Test
      void shouldSetIncompleteCheckTimeoutFromLegacyOperate() {
        assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
            .isEqualTo(180);
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          "camunda.operate.backup.repositoryName=repositoryName",
          "camunda.tasklist.backup.repositoryName=repositoryName",
        })
    class WithOperateAndTasklistLegacySet {
      final OperateProperties operateProperties;

      WithOperateAndTasklistLegacySet(@Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetRepositoryNameIfLegacyMatch() {
        assertThat(operateProperties.getBackup().getRepositoryName()).isEqualTo("repositoryName");
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          // new
          "camunda.data.secondary-storage.opensearch.backup.repository-name=repositoryName",
          "camunda.data.secondary-storage.opensearch.backup.snapshot-timeout=5",
          "camunda.data.secondary-storage.opensearch.backup.incomplete-check-timeout=3m",
          // old unified config
          "camunda.data.backup.repository-name=repositoryName",
          "camunda.data.backup.snapshot-timeout=2",
          "camunda.data.backup.incomplete-check-timeout=3m",
          // legacy configuration tasklist
          "camunda.tasklist.backup.repositoryName=repositoryName",
          // legacy configuration operate
          "camunda.operate.backup.repositoryName=repositoryName",
          "camunda.operate.backup.snapshotTimeout=2",
          "camunda.operate.backup.incompleteCheckTimeoutInSeconds=90",
        })
    class WithNewAndLegacySet {
      final OperateProperties operateProperties;

      WithNewAndLegacySet(@Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetRepositoryNameIfNewAndLegacyMatch() {
        assertThat(operateProperties.getBackup().getRepositoryName()).isEqualTo("repositoryName");
      }

      @Test
      void shouldSetSnapshotTimeoutFromNew() {
        assertThat(operateProperties.getBackup().getSnapshotTimeout()).isEqualTo(5);
      }

      @Test
      void shouldSetIncompleteCheckTimeoutFromNew() {
        assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
            .isEqualTo(180);
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          // old unified config
          "camunda.data.backup.incomplete-check-timeout=1m",
          // legacy configuration operate
          "camunda.operate.backup.incompleteCheckTimeoutInSeconds=90",
        })
    class IncompleteCheckTimeoutOldUnified {
      final OperateProperties operateProperties;

      IncompleteCheckTimeoutOldUnified(@Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetIncompleteCheckTimeoutFromOldUnified() {
        assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
            .isEqualTo(60);
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          // legacy configuration operate
          "camunda.operate.backup.incompleteCheckTimeoutInSeconds=90",
        })
    class IncompleteCheckTimeoutOnlyLegacy {
      final OperateProperties operateProperties;

      IncompleteCheckTimeoutOnlyLegacy(@Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetIncompleteCheckTimeoutFromLegacy() {
        assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
            .isEqualTo(90);
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          // new
          "camunda.data.secondary-storage.opensearch.backup.incomplete-check-timeout=2m",
          // legacy configuration operate
          "camunda.operate.backup.incompleteCheckTimeoutInSeconds=90",
        })
    class IncompleteCheckTimeoutFromNewWithLegacy {
      final OperateProperties operateProperties;

      IncompleteCheckTimeoutFromNewWithLegacy(
          @Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetIncompleteCheckTimeoutFromNew() {
        assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
            .isEqualTo(120);
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          // new
          "camunda.data.secondary-storage.opensearch.backup.incomplete-check-timeout=2m",
          // old unified config
          "camunda.data.backup.incomplete-check-timeout=1m",
        })
    class IncompleteCheckTimeoutFromNewWithOldUnified {
      final OperateProperties operateProperties;

      IncompleteCheckTimeoutFromNewWithOldUnified(
          @Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetIncompleteCheckTimeoutFromNew() {
        assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
            .isEqualTo(120);
      }
    }
  }

  @Nested
  @TestPropertySource(properties = {"camunda.data.secondary-storage.type=elasticsearch"})
  class Elasticsearch {

    @Nested
    @TestPropertySource(
        properties = {
          "camunda.data.secondary-storage.elasticsearch.backup.repository-name=repositoryNameNew",
          "camunda.data.secondary-storage.elasticsearch.backup.snapshot-timeout=5",
          "camunda.data.secondary-storage.elasticsearch.backup.incomplete-check-timeout=3m",
        })
    class WithOnlyUnifiedConfigSet {
      final OperateProperties operateProperties;

      WithOnlyUnifiedConfigSet(@Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetRepositoryName() {
        assertThat(operateProperties.getBackup().getRepositoryName())
            .isEqualTo("repositoryNameNew");
      }

      @Test
      void shouldSetSnapshotTimeout() {
        assertThat(operateProperties.getBackup().getSnapshotTimeout()).isEqualTo(5);
      }

      @Test
      void shouldSetIncompleteCheckTimeout() {
        assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
            .isEqualTo(180);
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          "camunda.data.backup.repository-name=repositoryNameNew",
          "camunda.data.backup.snapshot-timeout=5",
          "camunda.data.backup.incomplete-check-timeout=3m",
        })
    class WithOnlyOldUnifiedConfigSet {
      final OperateProperties operateProperties;

      WithOnlyOldUnifiedConfigSet(@Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetRepositoryName() {
        assertThat(operateProperties.getBackup().getRepositoryName())
            .isEqualTo("repositoryNameNew");
      }

      @Test
      void shouldSetSnapshotTimeout() {
        assertThat(operateProperties.getBackup().getSnapshotTimeout()).isEqualTo(5);
      }

      @Test
      void shouldSetIncompleteCheckTimeout() {
        assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
            .isEqualTo(180);
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          "camunda.tasklist.backup.repositoryName=repositoryNameTasklist",
        })
    class WithOnlyTasklistLegacySet {
      final OperateProperties operateProperties;

      WithOnlyTasklistLegacySet(@Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetRepositoryNameFromTasklist() {
        assertThat(operateProperties.getBackup().getRepositoryName())
            .isEqualTo("repositoryNameTasklist");
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          "camunda.operate.backup.repositoryName=repositoryNameOperate",
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
            .isEqualTo("repositoryNameOperate");
      }

      @Test
      void shouldSetSnapshotTimeoutFromLegacyOperate() {
        assertThat(operateProperties.getBackup().getSnapshotTimeout()).isEqualTo(5);
      }

      @Test
      void shouldSetIncompleteCheckTimeoutFromLegacyOperate() {
        assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
            .isEqualTo(180);
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          "camunda.operate.backup.repositoryName=repositoryName",
          "camunda.tasklist.backup.repositoryName=repositoryName",
        })
    class WithOperateAndTasklistLegacySet {
      final OperateProperties operateProperties;

      WithOperateAndTasklistLegacySet(@Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetRepositoryNameIfLegacyMatch() {
        assertThat(operateProperties.getBackup().getRepositoryName()).isEqualTo("repositoryName");
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          // new
          "camunda.data.secondary-storage.elasticsearch.backup.repository-name=repositoryName",
          "camunda.data.secondary-storage.elasticsearch.backup.snapshot-timeout=5",
          "camunda.data.secondary-storage.elasticsearch.backup.incomplete-check-timeout=3m",
          // old unified config
          "camunda.data.backup.repository-name=repositoryName",
          "camunda.data.backup.snapshot-timeout=2",
          "camunda.data.backup.incomplete-check-timeout=1m",
          // legacy configuration tasklist
          "camunda.tasklist.backup.repositoryName=repositoryName",
          // legacy configuration operate
          "camunda.operate.backup.repositoryName=repositoryName",
          "camunda.operate.backup.snapshotTimeout=1",
          "camunda.operate.backup.incompleteCheckTimeoutInSeconds=90",
        })
    class WithNewAndLegacySet {
      final OperateProperties operateProperties;

      WithNewAndLegacySet(@Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetRepositoryNameIfNewAndLegacyMatch() {
        assertThat(operateProperties.getBackup().getRepositoryName()).isEqualTo("repositoryName");
      }

      @Test
      void shouldSetSnapshotTimeoutFromNew() {
        assertThat(operateProperties.getBackup().getSnapshotTimeout()).isEqualTo(5);
      }

      @Test
      void shouldSetIncompleteCheckTimeoutFromNew() {
        assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
            .isEqualTo(180);
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          // old unified config
          "camunda.data.backup.incomplete-check-timeout=1m",
          // legacy configuration operate
          "camunda.operate.backup.incompleteCheckTimeoutInSeconds=90",
        })
    class IncompleteCheckTimeoutOldUnified {
      final OperateProperties operateProperties;

      IncompleteCheckTimeoutOldUnified(@Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetIncompleteCheckTimeoutFromOldUnified() {
        assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
            .isEqualTo(60);
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          // legacy configuration operate
          "camunda.operate.backup.incompleteCheckTimeoutInSeconds=90",
        })
    class IncompleteCheckTimeoutOnlyLegacy {
      final OperateProperties operateProperties;

      IncompleteCheckTimeoutOnlyLegacy(@Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetIncompleteCheckTimeoutFromLegacy() {
        assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
            .isEqualTo(90);
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          // new
          "camunda.data.secondary-storage.elasticsearch.backup.incomplete-check-timeout=2m",
          // legacy configuration operate
          "camunda.operate.backup.incompleteCheckTimeoutInSeconds=90",
        })
    class IncompleteCheckTimeoutFromNewWithLegacy {
      final OperateProperties operateProperties;

      IncompleteCheckTimeoutFromNewWithLegacy(
          @Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetIncompleteCheckTimeoutFromNew() {
        assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
            .isEqualTo(120);
      }
    }

    @Nested
    @TestPropertySource(
        properties = {
          // new
          "camunda.data.secondary-storage.elasticsearch.backup.incomplete-check-timeout=2m",
          // old unified config
          "camunda.data.backup.incomplete-check-timeout=1m",
        })
    class IncompleteCheckTimeoutFromNewWithOldUnified {
      final OperateProperties operateProperties;

      IncompleteCheckTimeoutFromNewWithOldUnified(
          @Autowired final OperateProperties operateProperties) {
        this.operateProperties = operateProperties;
      }

      @Test
      void shouldSetIncompleteCheckTimeoutFromNew() {
        assertThat(operateProperties.getBackup().getIncompleteCheckTimeoutInSeconds())
            .isEqualTo(120);
      }
    }
  }
}
