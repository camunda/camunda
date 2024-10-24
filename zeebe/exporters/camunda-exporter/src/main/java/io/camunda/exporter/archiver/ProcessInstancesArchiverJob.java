/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.archiver;

import io.camunda.webapps.schema.descriptors.operate.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessInstancesArchiverJob extends AbstractArchiverJob {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessInstancesArchiverJob.class);

  private final List<Integer> partitionIds;

  private final ArchiverUtil archiver;

  private final ListViewTemplate processInstanceTemplate;

  private final List<ProcessInstanceDependant> processInstanceDependantTemplates;

  //  private Metrics metrics;

  private ArchiverRepository archiverRepository;

  public ProcessInstancesArchiverJob(
      final ArchiverUtil archiver,
      final List<Integer> partitionIds,
      final ListViewTemplate processInstanceTemplate,
      final List<ProcessInstanceDependant> processInstanceDependantTemplates,
      final ScheduledExecutorService executor) {
    super(executor);
    this.partitionIds = partitionIds;
    this.archiver = archiver;
    this.processInstanceTemplate = processInstanceTemplate;
    this.processInstanceDependantTemplates = processInstanceDependantTemplates;
  }

  @Override
  public CompletableFuture<Integer> archiveBatch(final ArchiveBatch archiveBatch) {
    final CompletableFuture<Integer> archiveBatchFuture;

    if (archiveBatch != null) {
      LOGGER.debug("Following process instances are found for archiving: {}", archiveBatch);

      archiveBatchFuture = new CompletableFuture<Integer>();
      final var finishDate = archiveBatch.getFinishDate();
      final var processInstanceKeys = archiveBatch.getIds();

      moveDependableDocuments(finishDate, processInstanceKeys)
          .thenCompose(
              (v) -> {
                return moveProcessInstanceDocuments(finishDate, processInstanceKeys);
              })
          .thenAccept(
              (i) -> {
                // metrics.recordCounts(Metrics.COUNTER_NAME_ARCHIVED, i);
                archiveBatchFuture.complete(i);
              })
          .exceptionally(
              (t) -> {
                archiveBatchFuture.completeExceptionally(t);
                return null;
              });

    } else {
      LOGGER.debug("Nothing to archive");
      archiveBatchFuture = CompletableFuture.completedFuture(0);
    }

    return archiveBatchFuture;
  }

  @Override
  public CompletableFuture<ArchiveBatch> getNextBatch() {
    return archiverRepository.getProcessInstancesNextBatch(partitionIds);
  }

  private CompletableFuture<Void> moveDependableDocuments(
      final String finishDate, final List<Object> processInstanceKeys) {
    final var dependableFutures = new ArrayList<CompletableFuture<Void>>();

    for (final ProcessInstanceDependant template : processInstanceDependantTemplates) {
      final var moveDocumentsFuture =
          archiver.moveDocuments(
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

    archiver
        .moveDocuments(
            processInstanceTemplate.getFullQualifiedName(),
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
