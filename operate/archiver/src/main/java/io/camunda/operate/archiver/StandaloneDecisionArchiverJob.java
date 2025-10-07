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
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
public class StandaloneDecisionArchiverJob extends AbstractArchiverJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(StandaloneDecisionArchiverJob.class);

  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired private Metrics metrics;

  @Autowired private ArchiverRepository archiverRepository;

  private final Archiver archiver;
  private final List<Integer> partitionIds;

  public StandaloneDecisionArchiverJob(final Archiver archiver, final List<Integer> partitionIds) {
    this.archiver = archiver;
    this.partitionIds = partitionIds;
  }

  @Override
  public CompletableFuture<Integer> archiveBatch(final ArchiveBatch archiveBatch) {
    final CompletableFuture<Integer> archiveBatchFuture;

    if (archiveBatch != null) {
      LOGGER.debug(
          "Following standalone decision instances are found for archiving: {}", archiveBatch);

      archiveBatchFuture = new CompletableFuture<>();
      archiver
          .moveDocuments(
              decisionInstanceTemplate.getFullQualifiedName(),
              DecisionInstanceTemplate.ID,
              archiveBatch.getFinishDate(),
              archiveBatch.getIds())
          .whenComplete(
              (v, e) -> {
                if (e != null) {
                  archiveBatchFuture.completeExceptionally(e);
                  return;
                }
                metrics.recordCounts(
                    Metrics.COUNTER_NAME_STANDALONE_DECISIONS_ARCHIVED,
                    archiveBatch.getIds().size());
                archiveBatchFuture.complete(archiveBatch.getIds().size());
              });
    } else {
      LOGGER.debug("Nothing to archive");
      archiveBatchFuture = CompletableFuture.completedFuture(0);
    }

    return archiveBatchFuture;
  }

  @Override
  public CompletableFuture<ArchiveBatch> getNextBatch() {
    return archiverRepository.getStandaloneDecisionNextBatch(partitionIds);
  }
}
