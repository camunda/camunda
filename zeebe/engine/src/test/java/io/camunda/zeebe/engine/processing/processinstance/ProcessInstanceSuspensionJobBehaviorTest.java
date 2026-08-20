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
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class ProcessInstanceSuspensionJobBehaviorTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long CHILD_ELEMENT_KEY = 101L;
  private static final long JOB_KEY = 200L;
  private static final long CHILD_JOB_KEY = 201L;

  private MutableProcessingState processingState;

  private MutableElementInstanceState elementInstanceState;
  private MutableJobState jobState;
  private StateWriter stateWriter;
  private ProcessInstanceSuspensionJobBehavior behavior;

  @BeforeEach
  void setUp() {
    elementInstanceState = processingState.getElementInstanceState();
    jobState = processingState.getJobState();
    stateWriter = mock(StateWriter.class);
    behavior =
        new ProcessInstanceSuspensionJobBehavior(elementInstanceState, jobState, stateWriter);
  }

  @Test
  void shouldSuspendActivatableJob() {
    // given
    rootInstance();
    activatableJob(PROCESS_INSTANCE_KEY, JOB_KEY);

    // when
    behavior.suspendJobs(PROCESS_INSTANCE_KEY);

    // then
    verify(stateWriter).appendFollowUpEvent(eq(JOB_KEY), eq(JobIntent.SUSPENDED), any());
  }

  @Test
  void shouldSuspendJobWaitingForSecretResolution() {
    // given - suspension overrides secret-waiting, so a later resolution can't reactivate the job
    // while the instance stays suspended
    rootInstance();
    secretWaitingJob(PROCESS_INSTANCE_KEY, JOB_KEY);

    // when
    behavior.suspendJobs(PROCESS_INSTANCE_KEY);

    // then
    verify(stateWriter).appendFollowUpEvent(eq(JOB_KEY), eq(JobIntent.SUSPENDED), any());
  }

  @Test
  void shouldNotSuspendAlreadyActivatedJob() {
    // given - a job a worker already picked up is already off the activatable index; suspend has
    // nothing to do for it
    rootInstance();
    activatedJob(PROCESS_INSTANCE_KEY, JOB_KEY);

    // when
    behavior.suspendJobs(PROCESS_INSTANCE_KEY);

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), eq(JobIntent.SUSPENDED), any());
  }

  @Test
  void shouldSuspendJobsAcrossTheWholeElementInstanceTree() {
    // given - a job on the root and a job on a child element
    rootInstance();
    childInstance(CHILD_ELEMENT_KEY, PROCESS_INSTANCE_KEY);
    activatableJob(PROCESS_INSTANCE_KEY, JOB_KEY);
    activatableJob(CHILD_ELEMENT_KEY, CHILD_JOB_KEY);

    // when
    behavior.suspendJobs(PROCESS_INSTANCE_KEY);

    // then - the walk reaches both, not only the root's own job
    verify(stateWriter).appendFollowUpEvent(eq(JOB_KEY), eq(JobIntent.SUSPENDED), any());
    verify(stateWriter).appendFollowUpEvent(eq(CHILD_JOB_KEY), eq(JobIntent.SUSPENDED), any());
  }

  @Test
  void shouldDoNothingWhenProcessInstanceIsGone() {
    // given - no element instance seeded; getInstance returns null

    // when
    behavior.suspendJobs(PROCESS_INSTANCE_KEY);

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
  }

  @Test
  void shouldNotSuspendJobOfADifferentProcessInstance() {
    // given - defensive backstop: a child element instance carrying a different
    // processInstanceKey must still be filtered out, even though getChildren returns it as a
    // child of the root
    rootInstance();
    childInstance(CHILD_ELEMENT_KEY, 999L);
    activatableJob(CHILD_ELEMENT_KEY, CHILD_JOB_KEY);

    // when
    behavior.suspendJobs(PROCESS_INSTANCE_KEY);

    // then
    verify(stateWriter, never()).appendFollowUpEvent(eq(CHILD_JOB_KEY), any(), any());
  }

  @Test
  void shouldVisitOnlySuspendedJobs() {
    // given - one SUSPENDED job and one still-ACTIVATABLE job
    rootInstance();
    childInstance(CHILD_ELEMENT_KEY, PROCESS_INSTANCE_KEY);
    suspendedJob(PROCESS_INSTANCE_KEY, JOB_KEY);
    activatableJob(CHILD_ELEMENT_KEY, CHILD_JOB_KEY);
    final var visited = new ArrayList<Long>();

    // when
    behavior.forEachSuspendedJob(
        elementInstanceState.getInstance(PROCESS_INSTANCE_KEY),
        (jobKey, job) -> {
          visited.add(jobKey);
          return true;
        });

    // then
    assertThat(visited).containsExactly(JOB_KEY);
  }

  @Test
  void shouldStopTheWalkWhenVisitorReturnsFalse() {
    // given - two suspended jobs
    rootInstance();
    childInstance(CHILD_ELEMENT_KEY, PROCESS_INSTANCE_KEY);
    suspendedJob(PROCESS_INSTANCE_KEY, JOB_KEY);
    suspendedJob(CHILD_ELEMENT_KEY, CHILD_JOB_KEY);
    final var visited = new ArrayList<Long>();

    // when - the visitor stops after the first job it sees
    behavior.forEachSuspendedJob(
        elementInstanceState.getInstance(PROCESS_INSTANCE_KEY),
        (jobKey, job) -> {
          visited.add(jobKey);
          return false;
        });

    // then
    assertThat(visited).hasSize(1);
  }

  @Test
  void shouldNotVisitAnyJobWhenNoneAreSuspended() {
    // given
    rootInstance();
    activatableJob(PROCESS_INSTANCE_KEY, JOB_KEY);
    final var visited = new ArrayList<Long>();

    // when
    behavior.forEachSuspendedJob(
        elementInstanceState.getInstance(PROCESS_INSTANCE_KEY),
        (jobKey, job) -> {
          visited.add(jobKey);
          return true;
        });

    // then
    assertThat(visited).isEmpty();
  }

  private void rootInstance() {
    final var record = processInstanceRecord(PROCESS_INSTANCE_KEY);
    elementInstanceState.newInstance(
        PROCESS_INSTANCE_KEY, record, ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }

  private void childInstance(final long elementKey, final long processInstanceKey) {
    final var parent = elementInstanceState.getInstance(PROCESS_INSTANCE_KEY);
    final var record = processInstanceRecord(processInstanceKey);
    elementInstanceState.newInstance(
        parent, elementKey, record, ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }

  private void activatableJob(final long elementKey, final long jobKey) {
    final var record = jobRecord();
    jobState.insertJobRecordActivatable(jobKey, record);
    jobState.makeJobActivatableByPriority(
        record.getTypeBuffer(), jobKey, record.getTenantId(), record.getPriority());
    elementInstanceState.updateInstance(elementKey, ei -> ei.setJobKey(jobKey));
  }

  private void secretWaitingJob(final long elementKey, final long jobKey) {
    final var record = jobRecord();
    jobState.insertJobRecordActivatable(jobKey, record);
    jobState.makeJobActivatableByPriority(
        record.getTypeBuffer(), jobKey, record.getTenantId(), record.getPriority());
    jobState.parkForSecretResolution(jobKey, record);
    elementInstanceState.updateInstance(elementKey, ei -> ei.setJobKey(jobKey));
  }

  private void activatedJob(final long elementKey, final long jobKey) {
    final var record = jobRecord();
    jobState.insertJobRecordActivatable(jobKey, record);
    jobState.makeJobActivatableByPriority(
        record.getTypeBuffer(), jobKey, record.getTenantId(), record.getPriority());
    jobState.activate(jobKey, record);
    elementInstanceState.updateInstance(elementKey, ei -> ei.setJobKey(jobKey));
  }

  private void suspendedJob(final long elementKey, final long jobKey) {
    final var record = jobRecord();
    jobState.insertJobRecordActivatable(jobKey, record);
    jobState.makeJobActivatableByPriority(
        record.getTypeBuffer(), jobKey, record.getTenantId(), record.getPriority());
    jobState.updateJobState(jobKey, State.SUSPENDED);
    jobState.makeJobNotActivatable(jobKey, record);
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

  private static JobRecord jobRecord() {
    return new JobRecord()
        .setType("test")
        .setRetries(3)
        .setPriority(50)
        .setDeadline(256L)
        .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }
}
