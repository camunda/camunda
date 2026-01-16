/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.archiver.ArchiveBatch.ProcessInstanceArchiveBatch;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

/**
 * Archiver job for process instance data. This job handles the archiving of the process instance
 * records itself and also delegates to the repository to move dependent records (decisions, flow
 * node instances, variable updates, etc).
 */
public class ProcessInstanceArchiverJob extends ArchiverJob<ProcessInstanceArchiveBatch> {

  private final ListViewTemplate processInstanceTemplate;
  private final List<ProcessInstanceDependant> processInstanceDependants;

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
  }

  @Override
  String getJobName() {
    return "process-instance";
  }

  @Override
  CompletableFuture<ProcessInstanceArchiveBatch> getNextBatch() {
    return getArchiverRepository().getProcessInstancesNextBatch();
  }

  @Override
  ListViewTemplate getTemplateDescriptor() {
    return processInstanceTemplate;
  }

  @Override
  protected CompletableFuture<Integer> archive(
      final IndexTemplateDescriptor templateDescriptor, final ProcessInstanceArchiveBatch batch) {
    return archiveProcessDependants(batch)
        .thenComposeAsync(v -> super.archive(templateDescriptor, batch), getExecutor());
  }

  @Override
  protected Map<String, List<String>> createIdsByFieldMap(
      final IndexTemplateDescriptor templateDescriptor, final ProcessInstanceArchiveBatch batch) {
    final Map<String, List<String>> idsMap = new HashMap<>();
    final String processInstanceKeyField;
    final String rootProcessInstanceKeyField;
    switch (templateDescriptor) {
      case final ListViewTemplate ignored -> {
        processInstanceKeyField = ListViewTemplate.PROCESS_INSTANCE_KEY;
        rootProcessInstanceKeyField = ListViewTemplate.ROOT_PROCESS_INSTANCE_KEY;
      }
      case final ProcessInstanceDependant pid -> {
        processInstanceKeyField = pid.getProcessInstanceDependantField();
        rootProcessInstanceKeyField = pid.getRootProcessInstanceKeyField();
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported template descriptor: " + templateDescriptor.getClass().getName());
    }
    if (!batch.processInstanceKeys().isEmpty()) {
      idsMap.put(
          processInstanceKeyField,
          batch.processInstanceKeys().stream().map(String::valueOf).toList());
    }
    if (!batch.rootProcessInstanceKeys().isEmpty()) {
      idsMap.put(
          rootProcessInstanceKeyField,
          batch.rootProcessInstanceKeys().stream().map(String::valueOf).toList());
    }
    return idsMap;
  }

  private CompletableFuture<Void> archiveProcessDependants(
      final ProcessInstanceArchiveBatch batch) {
    final var futures = new ArrayList<CompletableFuture<?>>();

    for (final var dependant : processInstanceDependants) {
      futures.add(super.archive(dependant, batch));
    }

    return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
  }
}
