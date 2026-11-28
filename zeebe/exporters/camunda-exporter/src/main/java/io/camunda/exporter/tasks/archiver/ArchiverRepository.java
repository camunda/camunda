/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.index.TargetIndex;
import io.camunda.exporter.tasks.archiver.ArchiveBatch.BasicArchiveBatch;
import io.camunda.exporter.tasks.archiver.ArchiveBatch.ProcessInstanceArchiveBatch;
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

  CompletableFuture<ProcessInstanceArchiveBatch> getProcessInstancesNextBatch(final int size);

  CompletableFuture<BasicArchiveBatch> getBatchOperationsNextBatch();

  CompletableFuture<BasicArchiveBatch> getUsageMetricTUNextBatch();

  CompletableFuture<BasicArchiveBatch> getUsageMetricNextBatch();

  CompletableFuture<BasicArchiveBatch> getJobBatchMetricsNextBatch();

  CompletableFuture<BasicArchiveBatch> getStandaloneDecisionNextBatch();

  CompletableFuture<Void> setIndexLifeCycle(final String destinationIndexName);

  CompletableFuture<Void> setLifeCycleToAllIndexes();

  CompletableFuture<Void> moveDocumentsById(
      final String sourceIndexName,
      final String destinationIndexName,
      final Map<String, List<String>> keysByField,
      final Map<String, String> inclusionFilters,
      final Map<String, String> exclusionFilters,
      final Executor executor);

  /**
   * Same as {@link #moveDocumentsById}, but once the first document moved, the move is not stopped
   * when exporting gets paused. Use this for the documents that the next run needs in the source
   * index to find the batch again: if such a move stops halfway, the documents that remain can
   * become unreachable for the archiver.
   */
  default CompletableFuture<Void> moveDocumentsByIdToCompletion(
      final String sourceIndexName,
      final String destinationIndexName,
      final Map<String, List<String>> keysByField,
      final Map<String, String> inclusionFilters,
      final Map<String, String> exclusionFilters,
      final Executor executor) {
    return moveDocumentsById(
        sourceIndexName,
        destinationIndexName,
        keysByField,
        inclusionFilters,
        exclusionFilters,
        executor);
  }

  CompletableFuture<Integer> getCountOfProcessInstancesAwaitingArchival();

  default String getRetentionPolicyName(
      final String indexName, final RetentionConfiguration retentionConfiguration) {
    return INDEX_TO_RETENTION_POLICY_FIELD
        .getOrDefault(indexName, RetentionConfiguration::getPolicyName)
        .apply(retentionConfiguration);
  }

  default String buildHistoricalIndicesPattern(final IndexTemplateDescriptor indexTemplate) {
    return "%s,-%s,-%s,-%s%s*"
        .formatted(
            indexTemplate.getIndexPattern(),
            indexTemplate.getFullQualifiedName(),
            indexTemplate.getAlias(),
            indexTemplate.getFullQualifiedName(),
            TargetIndex.ORDINAL_SUFFIX_START);
  }
}
