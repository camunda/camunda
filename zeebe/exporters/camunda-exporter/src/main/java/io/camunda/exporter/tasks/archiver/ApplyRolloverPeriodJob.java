/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import io.camunda.exporter.tasks.BackgroundTask;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;

public class ApplyRolloverPeriodJob implements BackgroundTask {

  private final ArchiverRepository repository;
  private final Logger logger;

  public ApplyRolloverPeriodJob(final ArchiverRepository repository, final Logger logger) {
    this.repository = repository;
    this.logger = logger;
  }

  @Override
  public CompletionStage<Integer> execute() {
    return repository
        .setLifeCycleToAllIndexes()
        .thenApply(
            ignore -> {
              logger.debug("Applied ILM policy to all historical indices");
              return 0;
            });
  }

  @Override
  public String getCaption() {
    return "ILM policy application job";
  }
}
