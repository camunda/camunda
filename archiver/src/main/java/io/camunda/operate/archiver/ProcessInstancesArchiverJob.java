/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
public class ProcessInstancesArchiverJob extends AbstractArchiverJob {
  private static final Logger logger = LoggerFactory.getLogger(ProcessInstancesArchiverJob.class);

  private List<Integer> partitionIds;

  private Archiver archiver;

  @Autowired private ListViewTemplate processInstanceTemplate;

  @Autowired private List<ProcessInstanceDependant> processInstanceDependantTemplates;

  @Autowired private Metrics metrics;

  @Autowired private ArchiverRepository archiverRepository;

  public ProcessInstancesArchiverJob(Archiver archiver, List<Integer> partitionIds) {
    this.partitionIds = partitionIds;
    this.archiver = archiver;
  }

  @Override
  public CompletableFuture<ArchiveBatch> getNextBatch() {
    return archiverRepository.getProcessInstancesNextBatch(partitionIds);
  }

  @Override
  public CompletableFuture<Integer> archiveBatch(ArchiveBatch archiveBatch) {
    final CompletableFuture<Integer> archiveBatchFuture;

    if (archiveBatch != null) {
      logger.debug("Following process instances are found for archiving: {}", archiveBatch);

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
                metrics.recordCounts(Metrics.COUNTER_NAME_ARCHIVED, i);
                archiveBatchFuture.complete(i);
              })
          .exceptionally(
              (t) -> {
                archiveBatchFuture.completeExceptionally(t);
                return null;
              });

    } else {
      logger.debug("Nothing to archive");
      archiveBatchFuture = CompletableFuture.completedFuture(0);
    }

    return archiveBatchFuture;
  }

  private CompletableFuture<Void> moveDependableDocuments(
      final String finishDate, final List<Object> processInstanceKeys) {
    final var dependableFutures = new ArrayList<CompletableFuture<Void>>();

    for (ProcessInstanceDependant template : processInstanceDependantTemplates) {
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
