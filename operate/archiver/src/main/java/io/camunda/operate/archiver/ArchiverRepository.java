/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ArchiverRepository {
  CompletableFuture<ArchiveBatch> getBatchOperationNextBatch();

  CompletableFuture<ArchiveBatch> getProcessInstancesNextBatch(List<Integer> partitionIds);

  void setIndexLifeCycle(final String destinationIndexName);

  CompletableFuture<Void> deleteDocuments(
      final String sourceIndexName,
      final String idFieldName,
      final List<Object> processInstanceKeys);

  CompletableFuture<Void> reindexDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final String idFieldName,
      final List<Object> processInstanceKeys);
}
