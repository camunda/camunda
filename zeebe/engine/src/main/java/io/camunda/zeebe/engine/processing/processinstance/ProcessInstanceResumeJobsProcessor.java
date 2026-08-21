/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware;
import io.camunda.zeebe.engine.processing.streamprocessor.SuspensionAware.SuspensionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.NullMarked;

/**
 * Resumes one job of the process instance per {@code RESUME_JOBS} cycle: finds the next job still
 * {@link JobIntent#SUSPENDED}, writes {@link JobIntent#RESUMED} for it, publishes it via {@link
 * BpmnJobActivationBehavior#publishWork} — a job stream is push-only, so a stream worker learns
 * about the job only from that call — then appends the next {@code RESUME_JOBS}. Once none are
 * left, it appends {@code COMPLETE_RESUMING} to hand the resume finalization off to {@link
 * ProcessInstanceCompleteResumingProcessor}.
 *
 * <p>One job per cycle instead of all of them in this command's own batch: resuming a job that a
 * stream is waiting for also appends a {@code JobBatch.ACTIVATED} with its fetched variables.
 * Written in one batch per instance, that can exceed the max record batch size on a large instance
 * with many stream-activatable jobs — the same instance whose suspend could always fit in one
 * batch, since suspending writes only one bounded {@code Job.SUSPENDED} per job. Splitting resume
 * into one job per command batch bounds each cycle to a single job's own activation, the same limit
 * that already applies to any other job activation — see {@link
 * #shouldProcessResultsInSeparateBatches} for what actually keeps cycles from accumulating into one
 * another. Publishing is called unconditionally either way: {@link
 * BpmnJobActivationBehavior#publishWork} self-manages a hand-out that does not fit the batch by
 * leaving the job activatable for polling instead of failing the command (see {@code
 * JobSuspensionSecretResumeTest} for the resume-specific regression coverage of the same safety net
 * for secret references).
 *
 * <p>A cycle is rejected instead of appending a follow-up if the instance no longer exists
 * (cancelled mid-resume), its suspension marker is no longer {@code RESUMING} (a concurrent resume
 * chain already finalized it, see {@code ProcessInstanceCompleteResumingProcessor}), or the element
 * is ending ({@code ELEMENT_TERMINATING}/{@code ELEMENT_COMPLETING}): resuming and publishing a job
 * in any of those cases would act on a resume this cycle no longer owns.
 *
 * <p>{@link SuspensionBehavior#PROCESS} is unconditional: the marker is still {@code RESUMING} at
 * this point, and gating would strand the instance there forever.
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class ProcessInstanceResumeJobsProcessor
    implements TypedRecordProcessor<ProcessInstanceRecord>, SuspensionAware<ProcessInstanceRecord> {

  private static final String INSTANCE_GONE_MESSAGE =
      "Expected to resume the suspended jobs of process instance '%d', but it no longer exists — "
          + "likely cancelled while resuming.";
  private static final String ALREADY_FINALIZED_MESSAGE =
      "Expected to resume the suspended jobs of process instance '%d', but its suspension marker "
          + "is no longer RESUMING — likely already finalized by a concurrent resume.";
  private static final String LIFECYCLE_ENDING_MESSAGE =
      "Expected to resume the suspended jobs of process instance '%d', but it is %s — resume is "
          + "superseded by the ending lifecycle.";
  private static final Set<ProcessInstanceIntent> LIFECYCLE_ENDING_STATES =
      EnumSet.of(
          ProcessInstanceIntent.ELEMENT_TERMINATING, ProcessInstanceIntent.ELEMENT_COMPLETING);

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final ElementInstanceState elementInstanceState;
  private final SuspensionState suspensionState;
  private final ProcessInstanceSuspensionJobBehavior suspensionJobBehavior;
  private final BpmnJobActivationBehavior jobActivationBehavior;

  public ProcessInstanceResumeJobsProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final BpmnJobActivationBehavior jobActivationBehavior) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    rejectionWriter = writers.rejection();
    elementInstanceState = processingState.getElementInstanceState();
    suspensionState = processingState.getSuspensionState();
    suspensionJobBehavior =
        new ProcessInstanceSuspensionJobBehavior(
            elementInstanceState, processingState.getJobState(), stateWriter);
    this.jobActivationBehavior = jobActivationBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> command) {
    final long processInstanceKey = command.getKey();
    final var processInstance = elementInstanceState.getInstance(processInstanceKey);
    if (processInstance == null) {
      reject(command, INSTANCE_GONE_MESSAGE.formatted(processInstanceKey));
      return;
    }
    if (suspensionState.getSuspensionState(processInstanceKey) != SuspensionState.State.RESUMING) {
      reject(command, ALREADY_FINALIZED_MESSAGE.formatted(processInstanceKey));
      return;
    }
    final var lifecycleState = processInstance.getState();
    if (LIFECYCLE_ENDING_STATES.contains(lifecycleState)) {
      reject(command, LIFECYCLE_ENDING_MESSAGE.formatted(processInstanceKey, lifecycleState));
      return;
    }

    final long startAfterJobKey = command.getValue().getResumeFromJobKey();
    final long resumedJobKey = resumeNextJob(processInstance, startAfterJobKey);
    final var followUpValue = processInstance.getValue();
    final ProcessInstanceIntent nextIntent;
    if (resumedJobKey >= 0) {
      followUpValue.setResumeFromJobKey(resumedJobKey);
      nextIntent = ProcessInstanceIntent.RESUME_JOBS;
    } else {
      nextIntent = ProcessInstanceIntent.COMPLETE_RESUMING;
    }
    commandWriter.appendFollowUpCommand(processInstanceKey, nextIntent, followUpValue);
  }

  /**
   * Resumes and hands out the first still-suspended job found from {@code startAfterJobKey};
   * returns its key, or {@code -1} if none was found. Stops the walk after that one job: one per
   * cycle, see class javadoc.
   */
  private long resumeNextJob(final ElementInstance processInstance, final long startAfterJobKey) {
    final AtomicLong resumedJobKey = new AtomicLong(-1L);
    suspensionJobBehavior.forEachSuspendedJob(
        processInstance,
        startAfterJobKey,
        (jobKey, job) -> {
          stateWriter.appendFollowUpEvent(jobKey, JobIntent.RESUMED, job);
          jobActivationBehavior.publishWork(jobKey, job, new HashSet<>());
          resumedJobKey.set(jobKey);
          return false;
        });
    return resumedJobKey.get();
  }

  /**
   * A cycle's own writes are small on their own — one {@code Job.RESUMED}, at most one {@code
   * JobBatch.ACTIVATED}, one follow-up command — but without this override the stream processor
   * keeps reusing the same result builder across consecutive {@code RESUME_JOBS} cycles instead of
   * starting a fresh one per command, so an instance with many suspended jobs would still
   * accumulate every cycle's hand-out into that one shared batch until it exceeds the log's maximum
   * fragment size — the same reasoning {@code SecretReferenceBatchReactivateJobsProcessor} gives
   * for its own override. Isolating each cycle keeps the batch bounded by one job's hand-out,
   * matching what already holds for that job at creation time.
   */
  @Override
  public boolean shouldProcessResultsInSeparateBatches() {
    return true;
  }

  @Override
  public SuspensionBehavior suspensionBehavior(final TypedRecord<ProcessInstanceRecord> record) {
    return SuspensionBehavior.PROCESS;
  }

  private void reject(final TypedRecord<ProcessInstanceRecord> command, final String reason) {
    rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, reason);
  }
}
