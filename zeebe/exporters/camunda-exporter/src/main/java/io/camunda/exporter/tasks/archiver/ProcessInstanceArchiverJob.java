/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class ProcessInstanceArchiverJob extends ArchiverJob {

  private final ListViewTemplate processInstanceTemplate;
  private final List<ProcessInstanceDependant> processInstanceDependants;
  private final Executor executor;

  public ProcessInstanceArchiverJob(
      final ArchiverRepository repository,
      final ListViewTemplate processInstanceTemplate,
      final List<ProcessInstanceDependant> processInstanceDependants,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Executor executor) {
    super(
        repository,
        metrics,
        logger,
        executor,
        metrics::recordProcessInstancesArchiving,
        metrics::recordProcessInstancesArchived);
    this.processInstanceTemplate = processInstanceTemplate;
    this.processInstanceDependants =
        processInstanceDependants.stream()
            .sorted(Comparator.comparing(ProcessInstanceDependant::getFullQualifiedName))
            .toList(); // sort to ensure the execution order is stable
    this.executor = executor;
  }

  @Override
  String getJobName() {
    return "process-instance";
  }

  @Override
  CompletableFuture<ArchiveBatch> getNextBatch() {
    return getArchiverRepository().getProcessInstancesNextBatch();
  }

  @Override
  String getSourceIndexName() {
    return processInstanceTemplate.getFullQualifiedName();
  }

  @Override
  String getIdFieldName() {
    return ListViewTemplate.PROCESS_INSTANCE_KEY;
  }

  /**
   * Overridden to archive dependants first and then move the process instances themselves.
   *
   * @param sourceIdx process instance index
   * @param finishDate move to the dated index
   * @param idFieldName process instance key field
   * @param ids list of process instance keys to archive
   * @return number of archived process instances
   */
  @Override
  protected CompletableFuture<Integer> archive(
      final String sourceIdx,
      final String finishDate,
      final String idFieldName,
      final List<String> ids) {
    return archiveProcessDependants(finishDate, ids)
        .thenComposeAsync(v -> super.archive(sourceIdx, finishDate, idFieldName, ids), executor);
  }

  private CompletableFuture<Void> archiveProcessDependants(
      final String finishDate, final List<String> processInstanceKeys) {
    final var moveDependentDocuments =
        processInstanceDependants.stream()
            .map(
                dependant -> {
                  final var dependentSourceIdx = dependant.getFullQualifiedName();
                  final var dependentIdFieldName = dependant.getProcessInstanceDependantField();
                  return super.archive(
                      dependentSourceIdx, finishDate, dependentIdFieldName, processInstanceKeys);
                })
            .toArray(CompletableFuture[]::new);
    return CompletableFuture.allOf(moveDependentDocuments);
  }
}
