/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks;

import io.camunda.search.schema.OrdinalIndexManager;
import io.camunda.zeebe.exporter.common.tasks.BackgroundTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class OrdinalIndexTask implements BackgroundTask {
  private final OrdinalIndexManager ordinalIndexManager;

  public OrdinalIndexTask(final OrdinalIndexManager ordinalIndexManager) {
    this.ordinalIndexManager = ordinalIndexManager;
  }

  @Override
  public CompletionStage<Integer> execute() {
    ordinalIndexManager.ensureNextReady();
    return CompletableFuture.completedFuture(0);
  }
}
