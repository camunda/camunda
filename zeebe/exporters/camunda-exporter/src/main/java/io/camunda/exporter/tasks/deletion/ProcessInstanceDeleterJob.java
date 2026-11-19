/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import io.camunda.exporter.index.TargetIndexLocator;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.util.FilterByKeysQuery;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.zeebe.exporter.common.tasks.BackgroundTask;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class ProcessInstanceDeleterJob implements BackgroundTask {
  private static final Map<String, String> PROCESS_INSTANCE_FILTER =
      Map.of(ListViewTemplate.JOIN_RELATION, ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION);

  private final TargetIndexLocator targetIndexLocator;
  private final DeletionRepository deletionRepository;
  private final TemplateDeletes processInstanceDeletes;
  private final List<TemplateDeletes> dependantDeletes;
  private final CamundaExporterMetrics metrics;
  private final Logger logger;
  private final Executor executor;

  public ProcessInstanceDeleterJob(
      final TargetIndexLocator targetIndexLocator,
      final DeletionRepository deletionRepository,
      final ListViewTemplate listViewTemplate,
      final List<ProcessInstanceDependant> processInstanceDependants,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Executor executor) {
    this.targetIndexLocator = targetIndexLocator;
    this.deletionRepository = deletionRepository;
    processInstanceDeletes =
        new TemplateDeletes(listViewTemplate, PROCESS_INSTANCE_FILTER, Map.of());
    dependantDeletes = new ArrayList<>(processInstanceDependants.size() + 1);
    for (final var dependant : processInstanceDependants) {
      dependantDeletes.add(new TemplateDeletes(dependant, Map.of(), Map.of()));
    }
    // we can delete the non-process instance documents at the same time as the other dependent
    // docs.
    dependantDeletes.add(new TemplateDeletes(listViewTemplate, Map.of(), PROCESS_INSTANCE_FILTER));
    this.metrics = metrics;
    this.logger = logger;
    this.executor = executor;
  }

  @Override
  public CompletableFuture<Integer> execute() {
    final var timer = Timer.start();

    return deletionRepository
        .getProcessInstancesNextBatch()
        .thenComposeAsync(this::deleteBatch, executor)
        // measured after the whole run, so it includes searching and deleting
        .whenCompleteAsync((val, err) -> metrics.measureDeletionDuration(timer), executor);
  }

  private CompletableFuture<Integer> deleteBatch(final ProcessInstanceDeletionBatch batch) {
    if (batch == null || batch.isEmpty()) {
      logger.trace("No more process instances to delete.");
      return CompletableFuture.completedFuture(0);
    }

    logger.trace("Following process instances are found for deleting: {}", batch);
    metrics.recordProcessInstancesDeleting(batch.size());

    return deleteDependants(batch)
        .thenComposeAsync(
            // only delete the process instances after all dependants have been deleted, to avoid
            // orphaned documents
            ignored -> deleteProcessInstances(batch),
            executor);
  }

  private CompletableFuture<Void> deleteDependants(final ProcessInstanceDeletionBatch batch) {
    final var futures =
        dependantDeletes.stream()
            .map(deletes -> delete(deletes, batch))
            .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(futures);
  }

  private CompletableFuture<Integer> deleteProcessInstances(
      final ProcessInstanceDeletionBatch batch) {
    return delete(processInstanceDeletes, batch)
        .thenApply(
            ignored -> {
              // recorded before the future completes, so before the task is rescheduled
              metrics.recordProcessInstancesDeleted(batch.size());
              return batch.size();
            });
  }

  private CompletableFuture<Void> delete(
      final TemplateDeletes deletes, final ProcessInstanceDeletionBatch batch) {

    final var storageOrdinal = batch.storageOrdinal();
    final var template = deletes.template();
    final var index = targetIndexLocator.locateOrdinalIndex(template, storageOrdinal);
    return deletionRepository.deleteDocumentsById(
        index.name(), deletes.createFilter(batch), executor);
  }

  record TemplateDeletes(
      IndexTemplateDescriptor template,
      Map<String, String> inclusions,
      Map<String, String> exclusions) {
    FilterByKeysQuery<String> createFilter(final ProcessInstanceDeletionBatch batch) {
      return FilterByKeysQuery.of(batch.createIdsByFieldMap(template))
          .inclusions(inclusions)
          .exclusions(exclusions);
    }
  }
}
