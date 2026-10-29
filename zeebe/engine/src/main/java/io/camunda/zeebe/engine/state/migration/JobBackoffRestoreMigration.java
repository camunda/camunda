/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration;

import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.protocol.record.value.ErrorType;

/**
 * Removes job backoff entries that the engine could not have created at runtime. It used to also
 * re-add the entries lost to #14329, but guessing which jobs belong in backoff re-added jobs that
 * were waiting on an incident (#15954, #27183). The name is from that time, and #67522 tracks
 * removing this migration.
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
    final var processingState = context.processingState();
    final var incidentState = processingState.getIncidentState();
    processingState
        .getJobState()
        .removeInvalidBackoffs(jobKey -> canBackOff(incidentState, jobKey));
  }

  /**
   * A job with an open incident may not keep its backoff entry: it would recur while the incident
   * is open and raise a second incident for the same job. A secret resolution incident is the
   * exception (see #67521): resolving it does not reactivate a failed job, so that job only leaves
   * the failed state by recurring.
   */
  private static boolean canBackOff(final IncidentState incidentState, final long jobKey) {
    final long incidentKey = incidentState.getJobIncidentKey(jobKey);
    if (incidentKey == IncidentState.MISSING_INCIDENT) {
      return true;
    }
    final var incident = incidentState.getIncidentRecord(incidentKey);
    return incident != null && incident.getErrorType() == ErrorType.SECRET_RESOLUTION_ERROR;
  }
}
