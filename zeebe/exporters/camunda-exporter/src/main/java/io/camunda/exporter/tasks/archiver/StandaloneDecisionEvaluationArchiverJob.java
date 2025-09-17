/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import org.slf4j.Logger;

public class StandaloneDecisionEvaluationArchiverJob implements ArchiverJob {

  private final ArchiverRepository repository;
  private final DecisionInstanceTemplate template;
  private final Logger logger;
  private final Executor executor;

  public StandaloneDecisionEvaluationArchiverJob(
      final ArchiverRepository repository,
      final DecisionInstanceTemplate template,
      final Logger logger,
      final Executor executor) {
    this.repository = repository;
    this.template = template;
    this.logger = logger;
    this.executor = executor;
  }

  @Override
  public CompletionStage<Integer> archiveNextBatch() {
    final var nextBatch = repository.getStandaloneDecisionEvaluationsNextBatch();
    logger.info("Next batch DECISION EVAL: {}", nextBatch);
    // we want to get all decision instances wich do not have a process instance /definiton key
    // then we use the repository to move documents
    return CompletableFuture.completedFuture(0);
  }

  @Override
  public String getCaption() {
    return "Standalone decision evaluation archiver job";
  }
}
