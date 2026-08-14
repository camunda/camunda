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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.NullMarked;

/**
 * Un-parks one job of the process instance per {@code RESUME_JOBS} cycle: finds the next job still
 * suspended from {@link JobIntent#SUSPENDED}, writes {@link JobIntent#RESUMED} for it, publishes it
 * via {@link BpmnJobActivationBehavior#publishWork} — a job stream is push-only, so a stream worker
 * learns about the job only from that call — then appends the next {@code RESUME_JOBS}. Once none
 * are left, it appends {@code COMPLETE_RESUMING} to hand the resume finalization off to {@link
 * ProcessInstanceCompleteResumingProcessor}.
 *
 * <p>One job per cycle instead of all of them in this command's own batch: un-parking a job that a
 * stream is waiting for also appends a {@code JobBatch.ACTIVATED} with its fetched variables.
 * Written in one batch per instance, that can exceed the max record batch size on a large instance
 * with many stream-activatable jobs — the same instance that suspend was always able to park in one
 * batch, since parking writes only one bounded {@code Job.SUSPENDED} per job. Splitting resume into
 * one job per command batch bounds each cycle to a single job's own activation, the same limit that
 * already applies to any other job activation.
 *
 * <p>If the instance no longer exists (cancelled mid-resume), this cycle simply stops instead of
 * appending {@code COMPLETE_RESUMING}: that processor would reject on the same missing instance, so
 * appending it here would only add a record for a rejection nobody needs.
 *
 * <p>{@link SuspensionBehavior#PROCESS} is unconditional: the marker is still {@code RESUMING} at
 * this point, and gating would strand the instance there forever.
 */
@ExcludeAuthorizationCheck
@NullMarked
public final class ProcessInstanceResumeJobsProcessor
    implements TypedRecordProcessor<ProcessInstanceRecord>, SuspensionAware<ProcessInstanceRecord> {

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessInstanceSuspensionJobBehavior suspensionJobBehavior;
  private final BpmnJobActivationBehavior jobActivationBehavior;

  public ProcessInstanceResumeJobsProcessor(
      final ProcessingState processingState,
      final Writers writers,
      final BpmnJobActivationBehavior jobActivationBehavior) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    elementInstanceState = processingState.getElementInstanceState();
    suspensionJobBehavior =
        new ProcessInstanceSuspensionJobBehavior(
            elementInstanceState, processingState.getJobState(), stateWriter);
    this.jobActivationBehavior = jobActivationBehavior;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceRecord> command) {
    final long processInstanceKey = command.getKey();
    final var elementInstance = elementInstanceState.getInstance(processInstanceKey);
    if (elementInstance == null) {
      return;
    }

    final var parkedJob = suspensionJobBehavior.findNextParkedJob(processInstanceKey);
    if (parkedJob == null) {
      commandWriter.appendFollowUpCommand(
          processInstanceKey, ProcessInstanceIntent.COMPLETE_RESUMING, elementInstance.getValue());
      return;
    }

    stateWriter.appendFollowUpEvent(parkedJob.jobKey(), JobIntent.RESUMED, parkedJob.job());
    jobActivationBehavior.publishWork(parkedJob.jobKey(), parkedJob.job());
    commandWriter.appendFollowUpCommand(
        processInstanceKey, ProcessInstanceIntent.RESUME_JOBS, elementInstance.getValue());
  }

  @Override
  public SuspensionBehavior suspensionBehavior(final TypedRecord<ProcessInstanceRecord> record) {
    return SuspensionBehavior.PROCESS;
  }
}
