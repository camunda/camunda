/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.tasks.archiver.ArchiveBatch.BasicArchiveBatch;
import io.camunda.exporter.tasks.archiver.ArchiveBatch.ProcessInstanceArchiveBatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class TestRepository extends NoopArchiverRepository {
  final List<DocumentMove> moves = new ArrayList<>();
  ArchiveBatch batch;

  public <T extends ArchiveBatch> CompletableFuture<T> getNextBatch() {
    return CompletableFuture.completedFuture((T) batch);
  }

  @Override
  public CompletableFuture<ProcessInstanceArchiveBatch> getProcessInstancesNextBatch() {
    return CompletableFuture.completedFuture((ProcessInstanceArchiveBatch) batch);
  }

  @Override
  public CompletableFuture<BasicArchiveBatch> getBatchOperationsNextBatch() {
    return CompletableFuture.completedFuture((BasicArchiveBatch) batch);
  }

  @Override
  public CompletableFuture<BasicArchiveBatch> getUsageMetricTUNextBatch() {
    return CompletableFuture.completedFuture((BasicArchiveBatch) batch);
  }

  @Override
  public CompletableFuture<BasicArchiveBatch> getUsageMetricNextBatch() {
    return CompletableFuture.completedFuture((BasicArchiveBatch) batch);
  }

  @Override
  public CompletableFuture<BasicArchiveBatch> getJobBatchMetricsNextBatch() {
    return CompletableFuture.completedFuture((BasicArchiveBatch) batch);
  }

  @Override
  public CompletableFuture<BasicArchiveBatch> getStandaloneDecisionNextBatch() {
    return CompletableFuture.completedFuture((BasicArchiveBatch) batch);
  }

  @Override
  public CompletableFuture<Void> moveDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final Map<String, List<String>> keysByField,
      final Executor executor) {
    moves.add(new DocumentMove(sourceIndexName, destinationIndexName, keysByField, executor));
    return CompletableFuture.completedFuture(null);
  }

  record DocumentMove(
      String sourceIndexName,
      String destinationIndexName,
      Map<String, List<String>> keysByField,
      Executor executor) {}
}
