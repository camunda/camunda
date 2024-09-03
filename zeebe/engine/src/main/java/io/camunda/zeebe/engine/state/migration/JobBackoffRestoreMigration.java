/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

public class JobBackoffRestoreMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean needsToRun(final MigrationTaskContext context) {
    // The migration should run everytime, as there is no fast way to detect if a failed job need to
    // be migrated.
    // The only way to do it is retrieve the failed job twice, but this is too time-consuming
    // The migration should restore only the missing failed jobs
    return true;
  }

  @Override
  public void runMigration(final MutableMigrationTaskContext context) {
    context.processingState().getJobState().restoreBackoff();
  }
}
