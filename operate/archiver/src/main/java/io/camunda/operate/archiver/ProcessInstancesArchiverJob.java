/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import io.camunda.operate.Metrics;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
public class ProcessInstancesArchiverJob extends AbstractProcessInstancesArchiverJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessInstancesArchiverJob.class);

  @Autowired
  public ProcessInstancesArchiverJob(
      final Archiver archiver,
      final List<Integer> partitionIds,
      final ListViewTemplate processInstanceTemplate,
      final List<ProcessInstanceDependant> processInstanceDependantTemplates,
      final Metrics metrics,
      final ArchiverRepository archiverRepository) {
    super(
        archiver,
        partitionIds,
        processInstanceTemplate,
        processInstanceDependantTemplates,
        metrics,
        archiverRepository,
        LOGGER);
  }

  @Override
  protected CompletableFuture<Integer> archiveProcessInstances(final ArchiveBatch archiveBatch) {
    final var finishDate = archiveBatch.getFinishDate();
    final var processInstanceKeys = archiveBatch.getIds();

    return moveDependableDocuments(finishDate, processInstanceKeys)
        .thenCompose((v) -> moveProcessInstanceDocuments(finishDate, processInstanceKeys));
  }

  private CompletableFuture<Void> moveDependableDocuments(
      final String finishDate, final List<Object> processInstanceKeys) {
    final var dependableFutures = new ArrayList<CompletableFuture<Void>>();

    for (final ProcessInstanceDependant template : getProcessInstanceDependantTemplates()) {
      final var moveDocumentsFuture =
          getArchiver()
              .moveDocuments(
                  template.getFullQualifiedName(),
                  ProcessInstanceDependant.PROCESS_INSTANCE_KEY,
                  finishDate,
                  processInstanceKeys);
      dependableFutures.add(moveDocumentsFuture);
    }

    return CompletableFuture.allOf(
        dependableFutures.toArray(new CompletableFuture[dependableFutures.size()]));
  }

  private CompletableFuture<Integer> moveProcessInstanceDocuments(
      final String finishDate, final List<Object> processInstanceKeys) {
    final var future = new CompletableFuture<Integer>();

    getArchiver()
        .moveDocuments(
            getProcessInstanceTemplate().getFullQualifiedName(),
            ListViewTemplate.PROCESS_INSTANCE_KEY,
            finishDate,
            processInstanceKeys)
        .thenAccept((ignore) -> future.complete(processInstanceKeys.size()))
        .exceptionally(
            (t) -> {
              future.completeExceptionally(t);
              return null;
            });

    return future;
  }
}
