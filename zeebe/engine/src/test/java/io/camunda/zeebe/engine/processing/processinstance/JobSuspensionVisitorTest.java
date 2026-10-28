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

import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class JobSuspensionVisitorTest {

  private static final long PROCESS_INSTANCE_KEY = 100L;
  private static final long ELEMENT_KEY = 101L;
  private static final long JOB_KEY = 200L;

  private MutableProcessingState processingState;

  private MutableElementInstanceState elementInstanceState;
  private MutableJobState jobState;
  private StateWriter stateWriter;
  private JobSuspensionVisitor visitor;

  @BeforeEach
  void setUp() {
    elementInstanceState = processingState.getElementInstanceState();
    jobState = processingState.getJobState();
    stateWriter = mock(StateWriter.class);
    visitor =
        new JobSuspensionVisitor(
            stateWriter, jobState, new SuspensionMetrics(new SimpleMeterRegistry()));
    elementInstance();
  }

  @Test
  void shouldSuspendActivatableJob() {
    // given
    activatableJob();

    // when
    suspendElementInstance();

    // then
    verify(stateWriter).appendFollowUpEvent(eq(JOB_KEY), eq(JobIntent.SUSPENDED), any());
  }

  @Test
  void shouldSuspendJobWaitingForSecretResolution() {
    // given - suspension overrides secret-waiting, so a later resolution can't reactivate the job
    // while the instance stays suspended
    final var record = activatableJob();
    jobState.parkForSecretResolution(JOB_KEY, record);

    // when
    suspendElementInstance();

    // then
    verify(stateWriter).appendFollowUpEvent(eq(JOB_KEY), eq(JobIntent.SUSPENDED), any());
  }

  @Test
  void shouldNotSuspendAlreadyActivatedJob() {
    // given - a job a worker already picked up is off the activatable index
    final var record = activatableJob();
    jobState.activate(JOB_KEY, record);

    // when
    suspendElementInstance();

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
  }

  @Test
  void shouldNotSuspendAlreadySuspendedJob() {
    // given - a restarted or overlapping walk reaches a job it already suspended
    final var record = activatableJob();
    jobState.updateJobState(JOB_KEY, State.SUSPENDED);
    jobState.makeJobNotActivatable(JOB_KEY, record);

    // when
    suspendElementInstance();

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
  }

  @Test
  void shouldNotSuspendJobOfTerminatingElement() {
    // given - e.g. the canceling task listener job of an interrupted user task
    activatableJob();
    elementInstanceState.updateInstance(
        ELEMENT_KEY, ei -> ei.setState(ProcessInstanceIntent.ELEMENT_TERMINATING));

    // when
    suspendElementInstance();

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
  }

  @Test
  void shouldNotSuspendAnythingForElementWithoutJob() {
    // when
    suspendElementInstance();

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
  }

  private void suspendElementInstance() {
    final var elementInstance = elementInstanceState.getInstance(ELEMENT_KEY);
    if (visitor.shouldVisit(elementInstance)) {
      visitor.visit(elementInstance);
    }
  }

  private void elementInstance() {
    elementInstanceState.newInstance(
        PROCESS_INSTANCE_KEY,
        processInstanceRecord(BpmnElementType.PROCESS),
        ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.newInstance(
        elementInstanceState.getInstance(PROCESS_INSTANCE_KEY),
        ELEMENT_KEY,
        processInstanceRecord(BpmnElementType.SERVICE_TASK),
        ProcessInstanceIntent.ELEMENT_ACTIVATED);
  }

  private JobRecord activatableJob() {
    final var record =
        new JobRecord()
            .setType("test")
            .setRetries(3)
            .setPriority(50)
            .setDeadline(256L)
            .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
            .setElementInstanceKey(ELEMENT_KEY)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    jobState.insertJobRecordActivatable(JOB_KEY, record);
    jobState.makeJobActivatableByPriority(
        record.getTypeBuffer(), JOB_KEY, record.getTenantId(), record.getPriority());
    elementInstanceState.updateInstance(ELEMENT_KEY, ei -> ei.setJobKey(JOB_KEY));
    return record;
  }

  private static ProcessInstanceRecord processInstanceRecord(final BpmnElementType elementType) {
    return new ProcessInstanceRecord()
        .setElementId("process")
        .setBpmnProcessId("process")
        .setProcessInstanceKey(PROCESS_INSTANCE_KEY)
        .setFlowScopeKey(-1L)
        .setVersion(1)
        .setProcessDefinitionKey(1L)
        .setBpmnElementType(elementType);
  }
}
