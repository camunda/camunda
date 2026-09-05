/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSuspensionState;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

/**
 * Uses real element-instance/job/suspension state (via {@link ProcessingStateExtension}), mocking
 * only the writers and {@link BpmnJobActivationBehavior} — the same split {@code
 * BufferedCommandDrainProcessorTest} uses for the sibling processor in this resume chain. The
 * hand-out-declines-room case still needs the behavior mock to return false; everything about the
 * element and job state itself is real.
 */
@ExtendWith(ProcessingStateExtension.class)
public final class ProcessInstanceResumeJobsProcessorTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long CHILD_ELEMENT_KEY = 101L;
  private static final long JOB_KEY = 200L;
  private static final long CHILD_JOB_KEY = 201L;

  private MutableProcessingState processingState;

  private MutableElementInstanceState elementInstanceState;
  private MutableJobState jobState;
  private MutableSuspensionState suspensionState;
  private StateWriter stateWriter;
  private TypedCommandWriter commandWriter;
  private TypedRejectionWriter rejectionWriter;
  private BpmnJobActivationBehavior jobActivationBehavior;
  private ProcessInstanceResumeJobsProcessor processor;

  @BeforeEach
  void setUp() {
    elementInstanceState = processingState.getElementInstanceState();
    jobState = processingState.getJobState();
    suspensionState = processingState.getSuspensionState();
    stateWriter = mock(StateWriter.class);
    commandWriter = mock(TypedCommandWriter.class);
    rejectionWriter = mock(TypedRejectionWriter.class);
    jobActivationBehavior = mock(BpmnJobActivationBehavior.class);
    // a hand-out that takes the job, unless a test overrides it to prove the chain still advances
    // when it doesn't
    when(jobActivationBehavior.publishWork(anyLong(), any(), any())).thenReturn(true);

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.command()).thenReturn(commandWriter);
    when(writers.rejection()).thenReturn(rejectionWriter);

    processor =
        new ProcessInstanceResumeJobsProcessor(processingState, writers, jobActivationBehavior);

    // the cycle owns the resume by default: marker still RESUMING, as it is throughout a resume
    // that nothing else interferes with
    suspensionState.setSuspensionState(PROCESS_INSTANCE_KEY, SuspensionState.State.RESUMING);
  }

  @Test
  void shouldAppendCompleteResumingWhenNoParkedJobsRemain() {
    // given - the instance has no job at all, so there is nothing left to un-park
    rootInstance(ProcessInstanceIntent.ELEMENT_ACTIVATED);

    // when
    processor.processRecord(resumeJobsCommand());

    // then
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY),
            eq(ProcessInstanceIntent.COMPLETE_RESUMING),
            any(ProcessInstanceRecord.class));
    verify(commandWriter, never())
        .appendFollowUpCommand(anyLong(), eq(ProcessInstanceIntent.RESUME_JOBS), any());
    verify(jobActivationBehavior, never()).publishWork(anyLong(), any(), any());
    verify(rejectionWriter, never()).appendRejection(any(), any(), any());
  }

  @Test
  void shouldRequestSeparateBatchForEachCycle() {
    // given - without this, the RESUME_JOBS chain would accumulate every cycle's hand-out into
    // the same result builder instead of one job's per batch

    // when / then
    assertThat(processor.shouldProcessResultsInSeparateBatches()).isTrue();
  }

  @Test
  void shouldResumeOneParkedJobAndAppendNextResumeJobs() {
    // given - the instance still has one job parked from suspension
    rootInstance(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    parkJob(PROCESS_INSTANCE_KEY, JOB_KEY);

    // when
    processor.processRecord(resumeJobsCommand());

    // then - the job is un-parked and pushed/notified, and the loop continues instead of handing
    // off to COMPLETE_RESUMING in this same cycle
    verify(stateWriter).appendFollowUpEvent(eq(JOB_KEY), eq(JobIntent.RESUMED), any());
    verify(jobActivationBehavior).publishWork(eq(JOB_KEY), any(), any());
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY),
            eq(ProcessInstanceIntent.RESUME_JOBS),
            any(ProcessInstanceRecord.class));
    verify(commandWriter, never())
        .appendFollowUpCommand(anyLong(), eq(ProcessInstanceIntent.COMPLETE_RESUMING), any());
  }

  @Test
  void shouldCarryTheResumedJobKeyAsCursorOnTheNextResumeJobs() {
    // given - one parked job
    rootInstance(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    parkJob(PROCESS_INSTANCE_KEY, JOB_KEY);

    // when - resuming from the start (default -1 cursor)
    processor.processRecord(resumeJobsCommand());

    // then - the follow-up RESUME_JOBS carries the resumed job's key so the next cycle continues
    // after it (the inclusive re-seek then skips it, since it is no longer SUSPENDED)
    final var followUp = ArgumentCaptor.forClass(ProcessInstanceRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY), eq(ProcessInstanceIntent.RESUME_JOBS), followUp.capture());
    assertThat(followUp.getValue().getResumeFromJobKey()).isEqualTo(JOB_KEY);
  }

  @Test
  void shouldResumeFromTheCursorCarriedOnTheCommand() {
    // given - two parked jobs, ordered by key
    seedTwoParkedJobs();

    // when - the command's cursor points past the first job
    processor.processRecord(resumeJobsCommandFrom(CHILD_JOB_KEY));

    // then - only the job at/after the cursor is resumed; the earlier one is left untouched
    verify(stateWriter).appendFollowUpEvent(eq(CHILD_JOB_KEY), eq(JobIntent.RESUMED), any());
    verify(stateWriter, never()).appendFollowUpEvent(eq(JOB_KEY), eq(JobIntent.RESUMED), any());
    final var followUp = ArgumentCaptor.forClass(ProcessInstanceRecord.class);
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY), eq(ProcessInstanceIntent.RESUME_JOBS), followUp.capture());
    assertThat(followUp.getValue().getResumeFromJobKey()).isEqualTo(CHILD_JOB_KEY);
  }

  @Test
  void shouldResumeOnlyTheFirstOfSeveralParkedJobsInOneCycle() {
    // given - two parked jobs on two element instances of the same instance
    seedTwoParkedJobs();

    // when
    processor.processRecord(resumeJobsCommand());

    // then - one cycle un-parks and hands out exactly one, leaving the other for the next cycle
    verify(stateWriter, times(1)).appendFollowUpEvent(anyLong(), eq(JobIntent.RESUMED), any());
    verify(jobActivationBehavior, times(1)).publishWork(anyLong(), any(), any());
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY),
            eq(ProcessInstanceIntent.RESUME_JOBS),
            any(ProcessInstanceRecord.class));
  }

  @Test
  void shouldStillAdvanceTheChainWhenTheHandOutDoesNotFitTheBatch() {
    // given - one parked job, and a hand-out that reports the batch has no room for it
    rootInstance(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    parkJob(PROCESS_INSTANCE_KEY, JOB_KEY);
    when(jobActivationBehavior.publishWork(anyLong(), any(), any())).thenReturn(false);

    // when
    processor.processRecord(resumeJobsCommand());

    // then - the job is still un-parked (publishWork itself leaves it activatable for polling when
    // it doesn't fit, see its own contract) and the chain still advances instead of stalling
    verify(stateWriter).appendFollowUpEvent(eq(JOB_KEY), eq(JobIntent.RESUMED), any());
    verify(jobActivationBehavior).publishWork(eq(JOB_KEY), any(), any());
    verify(commandWriter)
        .appendFollowUpCommand(
            eq(PROCESS_INSTANCE_KEY),
            eq(ProcessInstanceIntent.RESUME_JOBS),
            any(ProcessInstanceRecord.class));
    verify(commandWriter, never())
        .appendFollowUpCommand(anyLong(), eq(ProcessInstanceIntent.COMPLETE_RESUMING), any());
  }

  @Test
  void shouldRejectWhenInstanceCancelledMidResume() {
    // given - no element instance seeded; getInstance returns null

    // when
    processor.processRecord(resumeJobsCommand());

    // then - neither a job nor COMPLETE_RESUMING is appended for a gone instance; the command is
    // rejected instead of silently dropped, so it isn't reconsidered
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
    verify(jobActivationBehavior, never()).publishWork(anyLong(), any(), any());
    final var reasonCaptor = ArgumentCaptor.forClass(String.class);
    verify(rejectionWriter)
        .appendRejection(any(), eq(RejectionType.INVALID_STATE), reasonCaptor.capture());
    assertThat(reasonCaptor.getValue()).contains("no longer exists");
  }

  @Test
  void shouldRejectWhenSuspensionMarkerNoLongerResuming() {
    // given - a concurrent resume chain already finalized (marker cleared) or restarted a fresh
    // one, and a later suspend re-parked the job before this stale cycle got to run
    rootInstance(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    parkJob(PROCESS_INSTANCE_KEY, JOB_KEY);
    suspensionState.setSuspensionState(PROCESS_INSTANCE_KEY, SuspensionState.State.SUSPENDED);

    // when
    processor.processRecord(resumeJobsCommand());

    // then - this stale cycle is rejected instead of un-parking a job the resume it belonged to
    // no longer owns
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
    verify(jobActivationBehavior, never()).publishWork(anyLong(), any(), any());
    final var reasonCaptor = ArgumentCaptor.forClass(String.class);
    verify(rejectionWriter)
        .appendRejection(any(), eq(RejectionType.INVALID_STATE), reasonCaptor.capture());
    assertThat(reasonCaptor.getValue()).contains("no longer RESUMING");
  }

  @Test
  void shouldRejectWhenInstanceIsTerminating() {
    // given - a cancel landed between two RESUME_JOBS cycles; the root is still present but ending
    rootInstance(ProcessInstanceIntent.ELEMENT_TERMINATING);

    // when
    processor.processRecord(resumeJobsCommand());

    // then - the cycle is rejected instead of handing a worker a job the termination is about to
    // cancel
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
    verify(jobActivationBehavior, never()).publishWork(anyLong(), any(), any());
    final var reasonCaptor = ArgumentCaptor.forClass(String.class);
    verify(rejectionWriter)
        .appendRejection(any(), eq(RejectionType.INVALID_STATE), reasonCaptor.capture());
    assertThat(reasonCaptor.getValue()).contains(ProcessInstanceIntent.ELEMENT_TERMINATING.name());
  }

  @Test
  void shouldRejectWhenInstanceIsCompleting() {
    // given - the root reached its own completing phase between two RESUME_JOBS cycles
    rootInstance(ProcessInstanceIntent.ELEMENT_COMPLETING);

    // when
    processor.processRecord(resumeJobsCommand());

    // then - the cycle is rejected instead of handing a worker a job the completion is about to
    // finish without
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
    verify(jobActivationBehavior, never()).publishWork(anyLong(), any(), any());
    final var reasonCaptor = ArgumentCaptor.forClass(String.class);
    verify(rejectionWriter)
        .appendRejection(any(), eq(RejectionType.INVALID_STATE), reasonCaptor.capture());
    assertThat(reasonCaptor.getValue()).contains(ProcessInstanceIntent.ELEMENT_COMPLETING.name());
  }

  /**
   * Seeds a root element instance holding {@code JOB_KEY} and a child holding {@code
   * CHILD_JOB_KEY}.
   */
  private void seedTwoParkedJobs() {
    rootInstance(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    childInstance(CHILD_ELEMENT_KEY);
    parkJob(PROCESS_INSTANCE_KEY, JOB_KEY);
    parkJob(CHILD_ELEMENT_KEY, CHILD_JOB_KEY);
  }

  private void rootInstance(final ProcessInstanceIntent state) {
    final var record = processInstanceRecord(PROCESS_INSTANCE_KEY);
    elementInstanceState.newInstance(PROCESS_INSTANCE_KEY, record, state);
  }

  private void childInstance(final long elementKey) {
    final var parent = elementInstanceState.getInstance(PROCESS_INSTANCE_KEY);
    final var record = processInstanceRecord(PROCESS_INSTANCE_KEY);
    elementInstanceState.newInstance(
        parent, elementKey, record, ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }

  /** Attaches a job to the given element and parks it, as if it had already been suspended. */
  private void parkJob(final long elementKey, final long jobKey) {
    final var jobRecord =
        new JobRecord()
            .setType("test")
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setRetries(3)
            .setPriority(50)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.insertJobRecordActivatable(jobKey, jobRecord);
    jobState.makeJobActivatableByPriority(
        jobRecord.getTypeBuffer(), jobKey, jobRecord.getTenantId(), jobRecord.getPriority());
    jobState.updateJobState(
        jobKey, io.camunda.zeebe.engine.state.immutable.JobState.State.SUSPENDED);
    jobState.makeJobNotActivatable(jobKey, jobRecord);
    elementInstanceState.updateInstance(elementKey, ei -> ei.setJobKey(jobKey));
  }

  private ProcessInstanceRecord processInstanceRecord(final long processInstanceKey) {
    return new ProcessInstanceRecord()
        .setElementId("process")
        .setBpmnProcessId("process")
        .setProcessInstanceKey(processInstanceKey)
        .setFlowScopeKey(-1L)
        .setVersion(1)
        .setProcessDefinitionKey(1L)
        .setBpmnElementType(BpmnElementType.PROCESS);
  }

  private MockTypedRecord<ProcessInstanceRecord> resumeJobsCommand() {
    return new MockTypedRecord<>(
        PROCESS_INSTANCE_KEY,
        new RecordMetadata(),
        new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY));
  }

  private MockTypedRecord<ProcessInstanceRecord> resumeJobsCommandFrom(final long cursor) {
    return new MockTypedRecord<>(
        PROCESS_INSTANCE_KEY,
        new RecordMetadata(),
        new ProcessInstanceRecord()
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setResumeFromJobKey(cursor));
  }
}
