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

public interface ArchiverJob extends BackgroundTask {
  CompletionStage<Integer> archiveNextBatch();

  @Override
  default CompletionStage<Integer> execute() {
    return archiveNextBatch();
  }
}