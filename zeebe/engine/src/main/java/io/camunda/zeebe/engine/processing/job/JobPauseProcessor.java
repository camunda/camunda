/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;

/**
 * Handles the {@link JobIntent#PAUSE} command — paired with {@code JobPausedApplier}, both gated
 * under {@code Capability.JOB_PAUSE_RESUME}. Admission gating is done at the cluster-version layer
 * ({@code ClusterVersionGate}) from the catalog's {@code GatedCommandId} listing; this processor
 * only runs once admission has accepted the command.
 *
 * <p>The precondition validator restricts state transitions to jobs currently in {@code
 * State.ACTIVATED} — the only state from which pausing is meaningful (an {@code ACTIVATABLE} job
 * has no worker holding it; {@code RESERVED}, {@code FAILED}, {@code ERROR_THROWN}, and {@code
 * PAUSED} reject naturally). On success the {@code PAUSED} event is appended; the applier removes
 * the activation deadline and flips the state column.
 */
@ExcludeAuthorizationCheck
public final class JobPauseProcessor implements TypedRecordProcessor<JobRecord> {

  private final JobState jobState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final JobCommandPreconditionValidator preconditionChecker;

  public JobPauseProcessor(
      final ProcessingState state,
      final Writers writers,
      final AuthorizationCheckBehavior authorizationCheckBehavior) {
    jobState = state.getJobState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    preconditionChecker =
        new JobCommandPreconditionValidator(
            jobState,
            state.getBannedInstanceState(),
            "pause",
            List.of(State.ACTIVATED),
            authorizationCheckBehavior);
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> record) {
    final var jobKey = record.getKey();
    preconditionChecker
        .check(record)
        .ifRightOrLeft(
            pausedJob -> stateWriter.appendFollowUpEvent(jobKey, JobIntent.PAUSED, pausedJob),
            rejection ->
                rejectionWriter.appendRejection(record, rejection.type(), rejection.reason()));
  }
}
