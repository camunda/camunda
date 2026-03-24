/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a batch of documents to be archived. This interface serves as a common contract for
 * different types of archive batches, such as {@link ProcessInstanceArchiveBatch} for process
 * instances or {@link BasicArchiveBatch} for other entities.
 *
 * <p>Each batch contains specific identifiers for the documents to be archived and a finish date
 * used for determining the destination index.
 */
public interface ArchiveBatch {

  String finishDate();

  int size();

  default boolean isEmpty() {
    return size() == 0;
  }

  record ProcessInstanceArchiveBatch(
      String finishDate, List<Long> processInstanceKeys, List<Long> rootProcessInstanceKeys)
      implements ArchiveBatch {

    @Override
    public int size() {
      return processInstanceKeys.size() + rootProcessInstanceKeys.size();
    }

    public List<ProcessInstanceArchiveBatch> chunk(final int chunkSize) {
      final var chunks = new ArrayList<ProcessInstanceArchiveBatch>();
      if (isEmpty()) {
        chunks.add(this);
        return chunks;
      }

      List<Long> currentRootProcessInstanceKeys = new ArrayList<>();
      List<Long> currentProcessInstanceKeys = new ArrayList<>();

      for (final var rootPi : rootProcessInstanceKeys()) {
        if (currentRootProcessInstanceKeys.size() >= chunkSize) {
          chunks.add(
              new ProcessInstanceArchiveBatch(
                  finishDate, currentProcessInstanceKeys, currentRootProcessInstanceKeys));
          currentRootProcessInstanceKeys = new ArrayList<>();
          currentProcessInstanceKeys = new ArrayList<>();
        }
        currentRootProcessInstanceKeys.add(rootPi);
      }

      for (final var pi : processInstanceKeys()) {
        if (currentRootProcessInstanceKeys.size() + currentProcessInstanceKeys.size()
            >= chunkSize) {
          chunks.add(
              new ProcessInstanceArchiveBatch(
                  finishDate, currentProcessInstanceKeys, currentRootProcessInstanceKeys));
          currentRootProcessInstanceKeys = new ArrayList<>();
          currentProcessInstanceKeys = new ArrayList<>();
        }
        currentProcessInstanceKeys.add(pi);
      }

      if (!currentRootProcessInstanceKeys.isEmpty() || !currentProcessInstanceKeys.isEmpty()) {
        chunks.add(
            new ProcessInstanceArchiveBatch(
                finishDate, currentProcessInstanceKeys, currentRootProcessInstanceKeys));
      }

      return chunks;
    }
  }

  record BasicArchiveBatch(String finishDate, List<String> ids) implements ArchiveBatch {

    @Override
    public int size() {
      return ids.size();
    }
  }

  record AuditLogCleanupBatch(
      String finishDate, List<String> auditLogCleanupIds, List<String> auditLogIds)
      implements ArchiveBatch {
    @Override
    public int size() {
      return auditLogCleanupIds.size() + auditLogIds.size();
    }
  }
}
