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

  CompletableFuture<ProcessInstanceArchiveBatch> getProcessInstancesNextBatch();

  CompletableFuture<BasicArchiveBatch> getBatchOperationsNextBatch();

  CompletableFuture<BasicArchiveBatch> getUsageMetricTUNextBatch();

  CompletableFuture<BasicArchiveBatch> getUsageMetricNextBatch();

  CompletableFuture<BasicArchiveBatch> getJobBatchMetricsNextBatch();

  CompletableFuture<BasicArchiveBatch> getStandaloneDecisionNextBatch();

  CompletableFuture<Void> setIndexLifeCycle(final String destinationIndexName);

  CompletableFuture<Void> setLifeCycleToAllIndexes();

  CompletableFuture<Void> deleteDocuments(
      final String sourceIndexName, final Map<String, List<String>> keysByField);

  CompletableFuture<Void> deleteDocuments(
      final String sourceIndexName,
      final Map<String, List<String>> keysByField,
      final Map<String, String> filters);

  CompletableFuture<Void> reindexDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final Map<String, List<String>> keysByField);

  CompletableFuture<Void> reindexDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final Map<String, List<String>> keysByField,
      final Map<String, String> filters);

  default CompletableFuture<Void> moveDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final Map<String, List<String>> keysByField,
      final Executor executor) {
    return moveDocuments(sourceIndexName, destinationIndexName, keysByField, Map.of(), executor);
  }

  default CompletableFuture<Void> moveDocuments(
      final String sourceIndexName,
      final String destinationIndexName,
      final Map<String, List<String>> keysByField,
      final Map<String, String> filters,
      final Executor executor) {
    return reindexDocuments(sourceIndexName, destinationIndexName, keysByField, filters)
        .thenComposeAsync(ok -> setIndexLifeCycle(destinationIndexName), executor)
        .thenComposeAsync(ok -> deleteDocuments(sourceIndexName, keysByField, filters), executor);
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
}
