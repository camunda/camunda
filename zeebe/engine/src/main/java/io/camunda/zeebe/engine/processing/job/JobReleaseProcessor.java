/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.identity.authorization.CslTenantCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * Releases a reserved job to the job workers: drops the reservation token from the stored job and
 * hands the job out, so the worker that would have served it all along — a connector runtime, for
 * one — picks it up and runs for real.
 *
 * <p>This is the counterpart of driving a reserved job by hand. A recorder reserves an instance's
 * jobs so nothing runs while it authors the step, then either completes a job itself (the job is
 * stubbed, the real work never happens) or releases it here (the real work happens). Releasing is
 * not a completion: the job stays {@code ACTIVATABLE} and the worker completes it as usual.
 *
 * <p>Only a caller supplying the reservation token may release, which is what stops anyone holding
 * the job key from un-reserving someone else's recording; the check is the same one that fences
 * every other command on a reserved job. The release is one-way — the token is gone from the job
 * record afterwards, so a timeout or a retry backoff keeps the job released.
 */
@NullMarked
public final class JobReleaseProcessor
    implements TypedRecordProcessor<JobRecord>, SuspensionAware<JobRecord> {

  private static final String NOT_RESERVED_MESSAGE =
      "Expected to release job with key '%d', but it is not reserved";
  private static final String CALL_ACTIVITY_STUB_MESSAGE =
      "Expected to release job with key '%d', but it stands in for the process a stubbed call "
          + "activity calls, which no job worker can run";

  private final JobCommandPreconditionValidator preconditionValidator;
  private final CslAuthorizationCheck cslCheck;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;

  public JobReleaseProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final BpmnJobActivationBehavior jobActivationBehavior,
      final CslAuthorizationCheck cslCheck,
      final CslTenantCheck tenantCheck) {
    preconditionValidator =
        new JobCommandPreconditionValidator(
            processingState.getJobState(),
            processingState.getBannedInstanceState(),
            "release",
            List.of(State.ACTIVATABLE),
            List.of(
                JobReservationFencingCheck.forCommand(),
                JobLeaseFencingCheck.forLifecycleCommand()),
            tenantCheck);
    this.cslCheck = cslCheck;
    this.jobActivationBehavior = jobActivationBehavior;
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> command) {
    final long jobKey = command.getKey();
    preconditionValidator
        .check(command)
        .flatMap(job -> checkReleasable(jobKey, job))
        .flatMap(job -> isAuthorized(command, job))
        .ifRightOrLeft(
            job -> release(jobKey, job, command),
            rejection -> {
              rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
              responseWriter.writeRejectedResponseOnCommand(
                  command, rejection.type(), rejection.reason());
            });
  }

  /**
   * Asks for the two reasons of {@link JobWorkerDispatch#withheldFromWorkersReason} separately
   * rather than through it: a reserved job is released back to the workers, which is the point of
   * this command, while a stub job has no worker to release it to.
   */
  private Either<Rejection, JobRecord> checkReleasable(final long jobKey, final JobRecord job) {
    if (job.isCallActivityStub()) {
      return Either.left(
          new Rejection(RejectionType.INVALID_STATE, CALL_ACTIVITY_STUB_MESSAGE.formatted(jobKey)));
    }
    if (!job.hasJobReservationToken()) {
      return Either.left(
          new Rejection(RejectionType.INVALID_STATE, NOT_RESERVED_MESSAGE.formatted(jobKey)));
    }
    return Either.right(job);
  }

  private Either<Rejection, JobRecord> isAuthorized(
      final TypedRecord<JobRecord> command, final JobRecord job) {
    return cslCheck.check(
        command,
        RequiredAuthorization.of(
            b -> b.processDefinition().updateProcessInstance().resourceId(job.getBpmnProcessId())),
        job,
        AuthorizationRejectionMapper.noPrincipal());
  }

  private void release(
      final long jobKey, final JobRecord job, final TypedRecord<JobRecord> command) {
    job.setJobReservationToken("");
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.RELEASED, job);
    // a job stream is push-only, so a stream worker learns about the job only from this call
    jobActivationBehavior.publishWork(jobKey, job);
    responseWriter.writeAcceptedResponseOnCommand(jobKey, JobIntent.RELEASED, job, command);
  }

  @Override
  public SuspensionAction onSuspended(final TypedRecord<JobRecord> record) {
    return SuspensionAction.REJECT;
  }

  @Override
  public SuspensionAction onResuming(final TypedRecord<JobRecord> record) {
    return SuspensionAction.REJECT;
  }
}
