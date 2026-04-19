/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.config.ExporterConfiguration.HistoryConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.tasks.archiver.ArchiveBatch.ProcessInstanceArchiveBatch;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

/**
 * This is an experimental archiver job for handling process instance data where we reindex
 * documents by id. Similar to the {@link ProcessInstanceArchiverJob} this job handles the archiving
 * of the process instance records itself and also delegates to the repository to move dependent
 * records (decisions, flow node instances, variable updates, etc).
 */
public class ProcessInstanceByIdArchiverJob extends ProcessInstanceArchiverJob {

  public ProcessInstanceByIdArchiverJob(
      final HistoryConfiguration config,
      final ArchiverRepository repository,
      final ListViewTemplate processInstanceTemplate,
      final List<ProcessInstanceDependant> processInstanceDependants,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final Executor executor) {
    super(
        config,
        repository,
        processInstanceTemplate,
        processInstanceDependants,
        metrics,
        logger,
        executor);
  }

  @Override
  String getJobName() {
    return "process-instance-by-id";
  }

  @Override
  protected CompletableFuture<Void> archiveProcessDependants(
      final ProcessInstanceArchiveBatch batch) {
    // get the usual process instance dependent archive tasks
    final List<CompletableFuture<?>> dependentFutures = getProcessDependentArchiveFutures(batch);

    // add archiving tasks to archive data from list-view in parallel with the dependent indexes.

    // Note: We can archive all data except documents with `joinRelation: processInstance`.
    // These must be moved last to avoid dangling data (i.e., child/dependent records that cannot
    // be moved independently of their parent instance). We use fields from the parent
    // document (e.g., `endDate`, `status`) to decide whether to archive documents from the
    // main index.

    // add archiving variables from the list view index as a parallel task
    dependentFutures.add(
        archive(
            getTemplateDescriptor(),
            batch,
            Map.of(ListViewTemplate.JOIN_RELATION, ListViewTemplate.VARIABLES_JOIN_RELATION)));

    // add archiving flownodes/activities from the list view index as a parallel task
    dependentFutures.add(
        archive(
            getTemplateDescriptor(),
            batch,
            Map.of(ListViewTemplate.JOIN_RELATION, ListViewTemplate.ACTIVITIES_JOIN_RELATION)));

    return CompletableFuture.allOf(dependentFutures.toArray(CompletableFuture[]::new));
  }

  @Override
  protected CompletableFuture<Integer> archive(
      final IndexTemplateDescriptor templateDescriptor,
      final ProcessInstanceArchiveBatch batch,
      final Map<String, String> filters) {
    final var sourceIdxName = templateDescriptor.getFullQualifiedName();
    final var idsMap = createIdsByFieldMap(templateDescriptor, batch);
    final var finishDate = batch.finishDate();
    return getArchiverRepository()
        .moveDocumentsById(
            sourceIdxName, sourceIdxName + finishDate, idsMap, filters, getExecutor())
        .thenApplyAsync(ok -> batch.size(), getExecutor());
  }
}
