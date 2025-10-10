/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.search.schema.config.RetentionConfiguration;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/** Placeholder interface for future abstracted access to the underlying storage (e.g. ES/OS). */
public interface ArchiverRepository extends AutoCloseable {
  // ILM policy name overrides
  // pending correct rollover implementation for metric indices
  // https://github.com/camunda/camunda/issues/34709
  Map<String, Function<RetentionConfiguration, String>> INDEX_TO_RETENTION_POLICY_FIELD =
      Map.of(
          UsageMetricTemplate.INDEX_NAME, RetentionConfiguration::getUsageMetricsPolicyName,
          UsageMetricTUTemplate.INDEX_NAME, RetentionConfiguration::getUsageMetricsPolicyName);

  CompletableFuture<ArchiveBatch> getProcessInstancesNextBatch();

  CompletableFuture<ArchiveBatch> getBatchOperationsNextBatch();

<<<<<<< HEAD
  CompletableFuture<ArchiveBatch> getUsageMetricTUNextBatch();

  CompletableFuture<ArchiveBatch> getUsageMetricNextBatch();

  CompletableFuture<ArchiveBatch> getStandaloneDecisionNextBatch();

||||||| 4f0d68366a8
=======
  CompletableFuture<ArchiveBatch> getUsageMetricTUNextBatch();

  CompletableFuture<ArchiveBatch> getUsageMetricNextBatch();

>>>>>>> origin/release-8.8.0
  CompletableFuture<Void> setIndexLifeCycle(final String destinationIndexName);

  CompletableFuture<Void> setLifeCycleToAllIndexes();

  CompletableFuture<Void> deleteDocuments(
      final String sourceIndexName,
      final String idFieldName,
      final List<String> processInstanceKeys);

  CompletableFuture<Void> reindexDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final String idFieldName,
      final List<String> processInstanceKeys);

  default CompletableFuture<Void> moveDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final String idFieldName,
      final List<String> ids,
      final Executor executor) {
    return reindexDocuments(sourceIndexName, destinationIndexName, idFieldName, ids)
        .thenComposeAsync(ok -> setIndexLifeCycle(destinationIndexName), executor)
        .thenComposeAsync(ok -> deleteDocuments(sourceIndexName, idFieldName, ids), executor);
  }

  CompletableFuture<Integer> getCountOfProcessInstancesAwaitingArchival();

  default String getRetentionPolicyName(
      final String indexName, final RetentionConfiguration retentionConfiguration) {
    return INDEX_TO_RETENTION_POLICY_FIELD
        .getOrDefault(indexName, RetentionConfiguration::getPolicyName)
        .apply(retentionConfiguration);
  }

  default String buildHistoricalIndicesPattern(final IndexTemplateDescriptor indexTemplate) {
    return "%s,-%s,-%s"
        .formatted(
            indexTemplate.getIndexPattern(),
            indexTemplate.getFullQualifiedName(),
            indexTemplate.getAlias());
  }

  class NoopArchiverRepository implements ArchiverRepository {

    @Override
    public CompletableFuture<ArchiveBatch> getProcessInstancesNextBatch() {
      return CompletableFuture.completedFuture(new ArchiveBatch("2024-01-01", List.of()));
    }

    @Override
    public CompletableFuture<ArchiveBatch> getBatchOperationsNextBatch() {
      return CompletableFuture.completedFuture(new ArchiveBatch("2024-01-01", List.of()));
    }

    @Override
<<<<<<< HEAD
    public CompletableFuture<ArchiveBatch> getUsageMetricTUNextBatch() {
      return CompletableFuture.completedFuture(new ArchiveBatch("2024-01-01", List.of()));
    }

    @Override
    public CompletableFuture<ArchiveBatch> getUsageMetricNextBatch() {
      return CompletableFuture.completedFuture(new ArchiveBatch("2024-01-01", List.of()));
    }

    @Override
    public CompletableFuture<ArchiveBatch> getStandaloneDecisionNextBatch() {
      return CompletableFuture.completedFuture(new ArchiveBatch("2024-01-01", List.of()));
    }

    @Override
||||||| 4f0d68366a8
=======
    public CompletableFuture<ArchiveBatch> getUsageMetricNextBatch() {
      return CompletableFuture.completedFuture(new ArchiveBatch("2024-01-01", List.of()));
    }

    @Override
    public CompletableFuture<ArchiveBatch> getUsageMetricTUNextBatch() {
      return CompletableFuture.completedFuture(new ArchiveBatch("2024-01-01", List.of()));
    }

    @Override
>>>>>>> origin/release-8.8.0
    public CompletableFuture<Void> setIndexLifeCycle(final String destinationIndexName) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> setLifeCycleToAllIndexes() {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteDocuments(
        final String sourceIndexName,
        final String idFieldName,
        final List<String> processInstanceKeys) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> reindexDocuments(
        final String sourceIndexName,
        final String destinationIndexName,
        final String idFieldName,
        final List<String> processInstanceKeys) {
      return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Integer> getCountOfProcessInstancesAwaitingArchival() {
      return CompletableFuture.completedFuture(0);
    }

    @Override
    public void close() throws Exception {}
  }
}
