/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
