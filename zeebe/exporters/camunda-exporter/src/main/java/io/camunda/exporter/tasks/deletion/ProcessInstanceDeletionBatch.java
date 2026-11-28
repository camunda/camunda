/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration.ProcessInstanceRetentionMode;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.zeebe.protocol.record.value.StorageOrdinalRelated;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A batch of finished process instances whose retention period has expired and whose data can be
 * deleted. All process instances in a batch belong to the same storage ordinal. Root process
 * instance keys identify whole PI hierarchies, while process instance keys identify individual
 * (legacy, or non-hierarchy mode) process instances.
 */
public record ProcessInstanceDeletionBatch(
    int storageOrdinal, List<Long> processInstanceKeys, List<Long> rootProcessInstanceKeys) {

  public static ProcessInstanceDeletionBatch empty() {
    return new ProcessInstanceDeletionBatch(
        StorageOrdinalRelated.NOT_ORDINAL_CONTROLLED, List.of(), List.of());
  }

  /**
   * Creates a batch from process instances sorted by storage ordinal. Only the leading process
   * instances sharing the storage ordinal of the first one are included; the rest are left for a
   * later batch. Process instances without a storage ordinal are treated as being in the main
   * index.
   *
   * @param processInstances process instances sorted by storage ordinal ascending
   * @param retentionMode controls whether root process instance keys are used
   * @return the batch, or an empty batch if there are no process instances
   */
  public static ProcessInstanceDeletionBatch from(
      final List<ProcessInstanceForListViewEntity> processInstances,
      final ProcessInstanceRetentionMode retentionMode) {
    if (processInstances.isEmpty()) {
      return empty();
    }

    final int storageOrdinal = storageOrdinalOf(processInstances.getFirst());
    final List<Long> processInstanceKeys = new ArrayList<>();
    final List<Long> rootProcessInstanceKeys = new ArrayList<>();

    processInstances.stream()
        .takeWhile(pi -> storageOrdinalOf(pi) == storageOrdinal)
        .forEach(
            pi -> {
              final var rootKey = pi.getRootProcessInstanceKey();
              if (retentionMode != ProcessInstanceRetentionMode.PI && rootKey != null) {
                rootProcessInstanceKeys.add(rootKey);
              } else {
                processInstanceKeys.add(pi.getKey());
              }
            });

    return new ProcessInstanceDeletionBatch(
        storageOrdinal, processInstanceKeys, rootProcessInstanceKeys);
  }

  public int size() {
    return processInstanceKeys.size() + rootProcessInstanceKeys.size();
  }

  public boolean isEmpty() {
    return size() == 0;
  }

  private static int storageOrdinalOf(final ProcessInstanceForListViewEntity processInstance) {
    return Objects.requireNonNullElse(
        processInstance.getStorageOrdinal(), StorageOrdinalRelated.MAIN_INDEX);
  }
}
