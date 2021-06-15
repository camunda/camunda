/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import java.util.EnumSet;
import java.util.Set;

final class IncidentResolvedApplier implements TypedEventApplier<IncidentIntent, IncidentRecord> {

  private static final Set<State> RESOLVABLE_JOB_STATES =
      EnumSet.of(State.FAILED, State.ERROR_THROWN);

  private final MutableIncidentState incidentState;
  private final MutableJobState jobState;

  public IncidentResolvedApplier(
      final MutableIncidentState incidentState, final MutableJobState jobState) {
    this.incidentState = incidentState;
    this.jobState = jobState;
  }

  @Override
  public void applyState(final long incidentKey, final IncidentRecord value) {
    final var jobKey = value.getJobKey();
    if (jobKey > 0) {
      // incident belonged to job
      final var stateOfJob = jobState.getState(jobKey);
      if (RESOLVABLE_JOB_STATES.contains(stateOfJob)) {
        final var job = jobState.getJob(jobKey);
        jobState.resolve(jobKey, job);
      }
    }
    incidentState.deleteIncident(incidentKey);
  }
}
