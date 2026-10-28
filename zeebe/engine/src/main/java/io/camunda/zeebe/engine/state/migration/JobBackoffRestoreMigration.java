/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

/**
 * Removes job backoff entries that the engine could not have created at runtime. It used to also
 * re-add the entries lost to #14329, but guessing which jobs belong in backoff re-added jobs that
 * were waiting on an incident (#15954, #27183). The name is kept from that time.
 */
public class JobBackoffRestoreMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean needsToRun(final MigrationTaskContext context) {
    // finding out whether an entry must be removed costs as much as removing it
    return true;
  }

  @Override
  public void runMigration(final MutableMigrationTaskContext context) {
    context.processingState().getJobState().removeInvalidBackoffs(jobKey -> true);
  }
}
