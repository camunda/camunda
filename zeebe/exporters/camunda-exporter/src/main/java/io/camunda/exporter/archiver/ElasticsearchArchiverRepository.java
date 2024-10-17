/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.archiver;

import java.util.List;
import java.util.concurrent.CompletableFuture;

// TODO: Implement. The existing implementation use a different ES client that what is used in the
// exporter
public class ElasticsearchArchiverRepository implements ArchiverRepository {

  @Override
  public CompletableFuture<ArchiveBatch> getBatchOperationNextBatch() {
    return null;
  }

  @Override
  public CompletableFuture<ArchiveBatch> getProcessInstancesNextBatch(
      final List<Integer> partitionIds) {
    return null;
  }

  @Override
  public void setIndexLifeCycle(final String destinationIndexName) {}

  @Override
  public CompletableFuture<Void> deleteDocuments(
      final String sourceIndexName,
      final String idFieldName,
      final List<Object> processInstanceKeys) {
    return null;
  }

  @Override
  public CompletableFuture<Void> reindexDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final String idFieldName,
      final List<Object> processInstanceKeys) {
    return null;
  }
}
