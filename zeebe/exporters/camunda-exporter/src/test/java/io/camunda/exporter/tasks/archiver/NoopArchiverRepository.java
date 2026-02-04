/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.tasks.archiver.ArchiveBatch.ProcessInstanceArchiveBatch;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class NoopArchiverRepository implements ArchiverRepository {

  @Override
  public CompletableFuture<ProcessInstanceArchiveBatch> getProcessInstancesNextBatch() {
    return CompletableFuture.completedFuture(
        new ArchiveBatch.ProcessInstanceArchiveBatch("2024-01-01", List.of(), List.of()));
  }

  @Override
  public CompletableFuture<ArchiveBatch.BasicArchiveBatch> getBatchOperationsNextBatch() {
    return CompletableFuture.completedFuture(
        new ArchiveBatch.BasicArchiveBatch("2024-01-01", List.of()));
  }

  @Override
  public CompletableFuture<ArchiveBatch.BasicArchiveBatch> getUsageMetricTUNextBatch() {
    return CompletableFuture.completedFuture(
        new ArchiveBatch.BasicArchiveBatch("2024-01-01", List.of()));
  }

  @Override
  public CompletableFuture<ArchiveBatch.BasicArchiveBatch> getUsageMetricNextBatch() {
    return CompletableFuture.completedFuture(
        new ArchiveBatch.BasicArchiveBatch("2024-01-01", List.of()));
  }

  @Override
  public CompletableFuture<ArchiveBatch.BasicArchiveBatch> getJobBatchMetricsNextBatch() {
    return CompletableFuture.completedFuture(
        new ArchiveBatch.BasicArchiveBatch("2024-01-01", List.of()));
  }

  @Override
  public CompletableFuture<ArchiveBatch.BasicArchiveBatch> getStandaloneDecisionNextBatch() {
    return CompletableFuture.completedFuture(
        new ArchiveBatch.BasicArchiveBatch("2024-01-01", List.of()));
  }

  @Override
  public CompletableFuture<Void> setIndexLifeCycle(final String destinationIndexName) {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> setLifeCycleToAllIndexes() {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> deleteDocuments(
      final String sourceIndexName, final Map<String, List<String>> keysByField) {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> reindexDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final Map<String, List<String>> keysByField) {
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Integer> getCountOfProcessInstancesAwaitingArchival() {
    return CompletableFuture.completedFuture(0);
  }

  @Override
  public void close() throws Exception {}
}
