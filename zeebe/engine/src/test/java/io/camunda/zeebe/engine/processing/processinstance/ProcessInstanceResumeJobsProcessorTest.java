/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.util.MockTypedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class ProcessInstanceResumeJobsProcessorTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long JOB_KEY = 200L;

  private ElementInstanceState elementInstanceState;
  private JobState jobState;
  private StateWriter stateWriter;
  private TypedCommandWriter commandWriter;
  private BpmnJobActivationBehavior jobActivationBehavior;
  private ProcessInstanceResumeJobsProcessor processor;

  @BeforeEach
  void setUp() {
    elementInstanceState = mock(ElementInstanceState.class);
    jobState = mock(JobState.class);
    stateWriter = mock(StateWriter.class);
    commandWriter = mock(TypedCommandWriter.class);
    jobActivationBehavior = mock(BpmnJobActivationBehavior.class);
    // no children by default: a leaf process instance with no parked jobs
    when(elementInstanceState.getChildren(anyLong())).thenReturn(List.of());

    final var writers = mock(Writers.class);
    when(writers.state()).thenReturn(stateWriter);
    when(writers.command()).thenReturn(commandWriter);

    final var processingState = mock(ProcessingState.class);
    when(processingState.getElementInstanceState()).thenReturn(elementInstanceState);
    when(processingState.getJobState()).thenReturn(jobState);

    processor =
        new ProcessInstanceResumeJobsProcessor(processingState, writers, jobActivationBehavior);
  }

  @Test
  void shouldAppendCompleteResumingWhenNoParkedJobsRemain() {
    // given - the instance has no job at all, so there is nothing left to un-park
    final var record = new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY);
    final var elementInstance = mock(ElementInstance.class);
    when(elementInstance.getValue()).thenReturn(record);
    when(elementInstanceState.getInstance(PROCESS_INSTANCE_KEY)).thenReturn(elementInstance);

    // when
    processor.processRecord(resumeJobsCommand());

    // then
    verify(commandWriter)
        .appendFollowUpCommand(
            PROCESS_INSTANCE_KEY, ProcessInstanceIntent.COMPLETE_RESUMING, record);
    verify(commandWriter, never())
        .appendFollowUpCommand(anyLong(), eq(ProcessInstanceIntent.RESUME_JOBS), any());
    verify(jobActivationBehavior, never()).publishWork(anyLong(), any());
  }

  @Test
  void shouldResumeOneParkedJobAndAppendNextResumeJobs() {
    // given - the instance still has one job parked from suspension
    final var record = new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY);
    final var elementInstance = mock(ElementInstance.class);
    when(elementInstance.getKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(elementInstance.getValue()).thenReturn(record);
    when(elementInstance.getJobKey()).thenReturn(JOB_KEY);
    when(elementInstanceState.getInstance(PROCESS_INSTANCE_KEY)).thenReturn(elementInstance);
    final var jobRecord = new JobRecord();
    when(jobState.getState(JOB_KEY)).thenReturn(State.SUSPENDED);
    when(jobState.getJob(JOB_KEY)).thenReturn(jobRecord);

    // when
    processor.processRecord(resumeJobsCommand());

    // then - the job is un-parked and pushed/notified, and the loop continues instead of handing
    // off to COMPLETE_RESUMING in this same cycle
    verify(stateWriter).appendFollowUpEvent(JOB_KEY, JobIntent.RESUMED, jobRecord);
    verify(jobActivationBehavior).publishWork(JOB_KEY, jobRecord);
    verify(commandWriter)
        .appendFollowUpCommand(PROCESS_INSTANCE_KEY, ProcessInstanceIntent.RESUME_JOBS, record);
    verify(commandWriter, never())
        .appendFollowUpCommand(anyLong(), eq(ProcessInstanceIntent.COMPLETE_RESUMING), any());
  }

  @Test
  void shouldStopWhenInstanceCancelledMidResume() {
    // given - no element instance seeded; getInstance returns null

    // when
    processor.processRecord(resumeJobsCommand());

    // then - neither a job nor COMPLETE_RESUMING is appended for a gone instance
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(commandWriter, never()).appendFollowUpCommand(anyLong(), any(), any());
    verify(jobActivationBehavior, never()).publishWork(anyLong(), any());
  }

  private MockTypedRecord<ProcessInstanceRecord> resumeJobsCommand() {
    return new MockTypedRecord<>(
        PROCESS_INSTANCE_KEY,
        new RecordMetadata(),
        new ProcessInstanceRecord().setProcessInstanceKey(PROCESS_INSTANCE_KEY));
  }
}
