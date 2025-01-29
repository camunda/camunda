/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import io.camunda.exporter.tasks.BackgroundTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class IncidentAlertTask implements BackgroundTask {

  @Override
  public CompletionStage<Integer> execute() {
    try {
      System.out.println("Incident alert task executed");
      return CompletableFuture.completedFuture(1);
    } catch (final Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }
}
