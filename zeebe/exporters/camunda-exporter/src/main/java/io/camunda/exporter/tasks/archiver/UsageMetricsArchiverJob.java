<<<<<<< HEAD
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import io.camunda.zeebe.util.FunctionUtil;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.slf4j.Logger;

public class UsageMetricsArchiverJob implements ArchiverJob {

  private final ArchiverRepository repository;
  private final IndexTemplateDescriptor usageMetricTemplateDescriptor;
  private final IndexTemplateDescriptor usageMetricTUTemplateDescriptor;
  private final CamundaExporterMetrics metrics;
  private final Logger logger;
  private final Executor executor;

  public UsageMetricsArchiverJob(
      final ArchiverRepository repository,
      final IndexTemplateDescriptor usageMetricTemplateDescriptor,
      final IndexTemplateDescriptor usageMetricTUTemplateDescriptor,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Executor executor) {
    this.repository = repository;
    this.usageMetricTemplateDescriptor = usageMetricTemplateDescriptor;
    this.usageMetricTUTemplateDescriptor = usageMetricTUTemplateDescriptor;
    this.metrics = metrics;
    this.logger = logger;
    this.executor = executor;
  }

  @Override
  public CompletionStage<Integer> archiveNextBatch() {
    // Run both batches (usage-metric and usage-metric-tu) in parallel and then sum results
    final CompletableFuture<Integer> usageMetricBatchFuture =
        repository
            .getUsageMetricNextBatch()
            .thenComposeAsync(
                batch ->
                    archiveBatch(
                        batch,
                        usageMetricTemplateDescriptor,
                        UsageMetricTemplate.ID,
                        metrics::recordUsageMetricsArchiving,
                        metrics::recordUsageMetricsArchived),
                executor)
            .toCompletableFuture();

    final CompletableFuture<Integer> usageMetricTuBatchFuture =
        repository
            .getUsageMetricTUNextBatch()
            .thenComposeAsync(
                batch ->
                    archiveBatch(
                        batch,
                        usageMetricTUTemplateDescriptor,
                        UsageMetricTUTemplate.ID,
                        metrics::recordUsageMetricsTUArchiving,
                        metrics::recordUsageMetricsTUArchived),
                executor)
            .toCompletableFuture();

    return usageMetricBatchFuture.thenCombineAsync(
        usageMetricTuBatchFuture, Integer::sum, executor);
  }

  private CompletionStage<Integer> archiveBatch(
      final ArchiveBatch batch,
      final IndexTemplateDescriptor templateDescriptor,
      final String idField,
      final Consumer<Integer> recordArchiving,
      final Consumer<Integer> recordArchived) {
    if (batch == null || batch.ids() == null || batch.ids().isEmpty()) {
      logger.trace(
          "Usage metrics archiver: nothing to archive for template {}",
          templateDescriptor.getIndexName());
      return CompletableFuture.completedFuture(0);
    }

    final List<String> ids = batch.ids();
    logger.trace(
        "Usage metrics archiver: archiving {} documents for template {} to suffix {}",
        ids.size(),
        templateDescriptor.getIndexName(),
        batch.finishDate());
    recordArchiving.accept(ids.size());

    return moveBatch(
            templateDescriptor.getFullQualifiedName(),
            templateDescriptor.getFullQualifiedName() + batch.finishDate(),
            idField,
            ids)
        .thenApplyAsync(FunctionUtil.peek(recordArchived), executor);
  }

  private CompletableFuture<Integer> moveBatch(
      final String sourceIndex,
      final String destinationIndexName,
      final String idField,
      final List<String> ids) {
    return repository
        .moveDocuments(sourceIndex, destinationIndexName, idField, ids, executor)
        .thenApplyAsync(ok -> ids.size(), executor);
  }

  @Override
  public String getCaption() {
    return "Usage metrics archiver job";
  }
}
||||||| 4f0d68366a8
=======
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTUTemplate;
import io.camunda.webapps.schema.descriptors.template.UsageMetricTemplate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class UsageMetricsArchiverJob implements ArchiverJob {

  private final ArchiverRepository repository;
  private final Logger logger;
  private final IndexTemplateDescriptor usageMetricTemplateDescriptor;
  private final IndexTemplateDescriptor usageMetricTUTemplateDescriptor;
  private final Executor executor;

  public UsageMetricsArchiverJob(
      final ArchiverRepository repository,
      final Logger logger,
      final IndexTemplateDescriptor usageMetricTemplateDescriptor,
      final IndexTemplateDescriptor usageMetricTUTemplateDescriptor,
      final Executor executor) {
    this.repository = repository;
    this.logger = logger;
    this.usageMetricTemplateDescriptor = usageMetricTemplateDescriptor;
    this.usageMetricTUTemplateDescriptor = usageMetricTUTemplateDescriptor;
    this.executor = executor;
  }

  @Override
  public CompletionStage<Integer> archiveNextBatch() {
    // Run both batches (usage-metric and usage-metric-tu) in parallel and then sum results
    final CompletableFuture<Integer> usageMetricBatchFuture =
        repository
            .getUsageMetricNextBatch()
            .thenComposeAsync(
                batch -> archiveBatch(batch, usageMetricTemplateDescriptor, UsageMetricTemplate.ID),
                executor)
            .toCompletableFuture();

    final CompletableFuture<Integer> usageMetricTuBatchFuture =
        repository
            .getUsageMetricTUNextBatch()
            .thenComposeAsync(
                batch ->
                    archiveBatch(batch, usageMetricTUTemplateDescriptor, UsageMetricTUTemplate.ID),
                executor)
            .toCompletableFuture();

    return usageMetricBatchFuture.thenCombineAsync(
        usageMetricTuBatchFuture, Integer::sum, executor);
  }

  private CompletionStage<Integer> archiveBatch(
      final ArchiveBatch batch,
      final IndexTemplateDescriptor templateDescriptor,
      final String idField) {
    if (batch == null || batch.ids() == null || batch.ids().isEmpty()) {
      logger.trace(
          "Usage metrics archiver: nothing to archive for template {}",
          templateDescriptor.getIndexName());
      return CompletableFuture.completedFuture(0);
    }

    final List<String> ids = batch.ids();
    logger.trace(
        "Usage metrics archiver: archiving {} documents for template {} to suffix {}",
        ids.size(),
        templateDescriptor.getIndexName(),
        batch.finishDate());

    return repository
        .moveDocuments(
            templateDescriptor.getFullQualifiedName(),
            templateDescriptor.getFullQualifiedName() + batch.finishDate(),
            idField,
            ids,
            executor)
        .thenApplyAsync(ok -> ids.size(), executor);
  }

  @Override
  public String getCaption() {
    return "Usage metrics archiver job";
  }
}
>>>>>>> origin/release-8.8.0
