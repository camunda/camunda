/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

public class JobTimeoutCleanupMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean needsToRun(final MigrationTaskContext context) {
    // There is currently no way to know whether this must be run that is faster than running the
    // migration task itself. I.e. in order to know if there are entries that must be removed, we
    // must find and check them first. We don't expect that this migration will take long to run, as
    // it only hits active jobs (with a timeout). There are likely never many of these
    // simultaneously.
    return true;
  }

  @Override
  public void runMigration(final MutableMigrationTaskContext context) {
    context.processingState().getJobState().cleanupTimeoutsWithoutJobs();
  }
}
