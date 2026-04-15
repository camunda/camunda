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
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import java.util.List;
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
  protected CompletableFuture<Integer> archive(
      final String sourceIdxName,
      final String idField,
      final String finishDate,
      final List<String> processInstanceKeys) {
    return getRepository()
        .moveDocumentsById(
            sourceIdxName, sourceIdxName + finishDate, idField, processInstanceKeys, getExecutor())
        .thenApplyAsync(ok -> processInstanceKeys.size(), getExecutor());
  }

  @Override
  public String getCaption() {
    return "Process instances by ID archiver job";
  }
}
