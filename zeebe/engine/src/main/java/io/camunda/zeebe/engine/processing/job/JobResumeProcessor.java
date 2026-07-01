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
 * Handles the {@link JobIntent#RESUME} command. Mirror of {@code JobPauseProcessor}; both are gated
 * under {@code Capability.JOB_PAUSE_RESUME}. Admission is gated at the cluster-version layer from
 * the catalog's {@code GatedCommandId} listing.
 *
 * <p>The precondition validator restricts resume to jobs currently in {@code State.PAUSED} — the
 * only state in which a resume transition is meaningful. The {@code RESUMED} event is appended; the
 * applier re-registers the activation deadline and flips the state column back to {@code
 * ACTIVATED}.
 */
@ExcludeAuthorizationCheck
public final class JobResumeProcessor implements TypedRecordProcessor<JobRecord> {

  private final JobState jobState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final JobCommandPreconditionValidator preconditionChecker;

  public JobResumeProcessor(
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
            "resume",
            List.of(State.PAUSED),
            authorizationCheckBehavior);
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> record) {
    final var jobKey = record.getKey();
    preconditionChecker
        .check(record)
        .ifRightOrLeft(
            resumedJob -> stateWriter.appendFollowUpEvent(jobKey, JobIntent.RESUMED, resumedJob),
            rejection ->
                rejectionWriter.appendRejection(record, rejection.type(), rejection.reason()));
  }
}
