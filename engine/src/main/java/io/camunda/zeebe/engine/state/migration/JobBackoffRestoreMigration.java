/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;

public class JobBackoffRestoreMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean needsToRun(final ProcessingState processingState) {
    // The migration should run only if there are failed jobs in the state, but the backoff column
    // family is empty or there are more failed jobs then record inside the backoff column family
    return processingState.getJobState().isJobBackoffToRestore();
  }

  @Override
  public void runMigration(final MutableProcessingState processingState) {
    processingState.getJobState().restoreBackoff();
  }
}
