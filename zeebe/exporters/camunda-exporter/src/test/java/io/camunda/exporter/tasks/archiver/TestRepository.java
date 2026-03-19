/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.ArchiverJobMetrics;
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

  public <T extends ArchiveBatch> CompletableFuture<T> getNextBatch(
      final ArchiverJobMetrics archiverJobMetrics) {
    return CompletableFuture.completedFuture((T) batch);
  }

  @Override
  public CompletableFuture<ProcessInstanceArchiveBatch> getProcessInstancesNextBatch(
      final ArchiverJobMetrics archiverJobMetrics) {
    return CompletableFuture.completedFuture((ProcessInstanceArchiveBatch) batch);
  }

  @Override
  public CompletableFuture<BasicArchiveBatch> getBatchOperationsNextBatch(
      final ArchiverJobMetrics archiverJobMetrics) {
    return CompletableFuture.completedFuture((BasicArchiveBatch) batch);
  }

  @Override
  public CompletableFuture<BasicArchiveBatch> getUsageMetricTUNextBatch(
      final ArchiverJobMetrics archiverJobMetrics) {
    return CompletableFuture.completedFuture((BasicArchiveBatch) batch);
  }

  @Override
  public CompletableFuture<BasicArchiveBatch> getUsageMetricNextBatch(
      final ArchiverJobMetrics archiverJobMetrics) {
    return CompletableFuture.completedFuture((BasicArchiveBatch) batch);
  }

  @Override
  public CompletableFuture<BasicArchiveBatch> getJobBatchMetricsNextBatch(
      final ArchiverJobMetrics archiverJobMetrics) {
    return CompletableFuture.completedFuture((BasicArchiveBatch) batch);
  }

  @Override
  public CompletableFuture<BasicArchiveBatch> getStandaloneDecisionNextBatch(
      final ArchiverJobMetrics archiverJobMetrics) {
    return CompletableFuture.completedFuture((BasicArchiveBatch) batch);
  }

  @Override
  public CompletableFuture<Void> moveDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final Map<String, List<String>> keysByField,
      final Map<String, String> filters,
      final ArchiverJobMetrics archiveMetrics,
      final Executor executor) {
    moves.add(
        new DocumentMove(sourceIndexName, destinationIndexName, keysByField, filters, executor));
    return CompletableFuture.completedFuture(null);
  }

  record DocumentMove(
      String sourceIndexName,
      String destinationIndexName,
      Map<String, List<String>> keysByField,
      Map<String, String> filters,
      Executor executor) {
    public DocumentMove(
        final String sourceIndexName,
        final String destinationIndexName,
        final Map<String, List<String>> keysByField,
        final Executor executor) {
      this(sourceIndexName, destinationIndexName, keysByField, Map.of(), executor);
    }
  }
}
