/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.tasks.archiver.ArchiverRepository.NoopArchiverRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class TestRepository extends NoopArchiverRepository {
  final List<DocumentMove> moves = new ArrayList<>();
  ArchiveBatch batch;

  public CompletableFuture<ArchiveBatch> getNextBatch() {
    return CompletableFuture.completedFuture(batch);
  }

  @Override
  public CompletableFuture<ArchiveBatch> getProcessInstancesNextBatch() {
    return CompletableFuture.completedFuture(batch);
  }

  @Override
  public CompletableFuture<ArchiveBatch> getBatchOperationsNextBatch() {
    return CompletableFuture.completedFuture(batch);
  }

  @Override
  public CompletableFuture<ArchiveBatch> getUsageMetricTUNextBatch() {
    return CompletableFuture.completedFuture(batch);
  }

  @Override
  public CompletableFuture<ArchiveBatch> getUsageMetricNextBatch() {
    return CompletableFuture.completedFuture(batch);
  }

  @Override
  public CompletableFuture<ArchiveBatch> getStandaloneDecisionNextBatch() {
    return CompletableFuture.completedFuture(batch);
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
