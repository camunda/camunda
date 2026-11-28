/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration.ProcessInstanceRetentionMode;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessInstanceDeletionBatchTest {

  @Test
  void shouldCreateEmptyBatchWithoutStorageOrdinal() {
    // when
    final var batch = ProcessInstanceDeletionBatch.from(List.of(), ProcessInstanceRetentionMode.PI);

    // then
    assertThat(batch.isEmpty()).isTrue();
    assertThat(batch.storageOrdinal()).isEqualTo(StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED);
  }

  @Test
  void shouldOnlyIncludeProcessInstancesWithStorageOrdinalOfFirst() {
    // given
    final var processInstances =
        List.of(
            processInstance(1L, 1, null),
            processInstance(2L, 1, null),
            processInstance(3L, 2, null),
            processInstance(4L, 2, null));

    // when
    final var batch =
        ProcessInstanceDeletionBatch.from(processInstances, ProcessInstanceRetentionMode.PI);

    // then
    assertThat(batch.storageOrdinal()).isEqualTo(1);
    assertThat(batch.processInstanceKeys()).containsExactly(1L, 2L);
    assertThat(batch.rootProcessInstanceKeys()).isEmpty();
  }

  @Test
  void shouldTreatMissingStorageOrdinalAsMainIndex() {
    // given
    final var processInstances =
        List.of(
            processInstance(1L, null, null),
            processInstance(2L, StorageOrdinalRelated.MAIN_INDEX, null),
            processInstance(3L, 1, null));

    // when
    final var batch =
        ProcessInstanceDeletionBatch.from(processInstances, ProcessInstanceRetentionMode.PI);

    // then
    assertThat(batch.storageOrdinal()).isEqualTo(StorageOrdinalRelated.MAIN_INDEX);
    assertThat(batch.processInstanceKeys()).containsExactly(1L, 2L);
  }

  @Test
  void shouldIgnoreRootProcessInstanceKeysInPiMode() {
    // given
    final var processInstances = List.of(processInstance(1L, 1, 1L), processInstance(2L, 1, 2L));

    // when
    final var batch =
        ProcessInstanceDeletionBatch.from(processInstances, ProcessInstanceRetentionMode.PI);

    // then
    assertThat(batch.processInstanceKeys()).containsExactly(1L, 2L);
    assertThat(batch.rootProcessInstanceKeys()).isEmpty();
  }

  @Test
  void shouldSplitRootAndLegacyProcessInstancesInHierarchyMode() {
    // given
    final var processInstances = List.of(processInstance(1L, 1, 1L), processInstance(2L, 1, null));

    // when
    final var batch =
        ProcessInstanceDeletionBatch.from(
            processInstances, ProcessInstanceRetentionMode.PI_HIERARCHY);

    // then
    assertThat(batch.storageOrdinal()).isEqualTo(1);
    assertThat(batch.rootProcessInstanceKeys()).containsExactly(1L);
    assertThat(batch.processInstanceKeys()).containsExactly(2L);
  }

  private static ProcessInstanceForListViewEntity processInstance(
      final long key, final Integer storageOrdinal, final Long rootProcessInstanceKey) {
    return new ProcessInstanceForListViewEntity()
        .setKey(key)
        .setStorageOrdinal(storageOrdinal)
        .setRootProcessInstanceKey(rootProcessInstanceKey);
  }
}
