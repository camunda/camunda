/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.container;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.behavior.AgentInstanceBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBufferedMessageStartEventBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnCompensationSubscriptionBehaviour;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnProcessDeletionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnProcessResultSenderBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class ProcessProcessorTest {

  private static final long PROCESS_INSTANCE_KEY = 42L;

  private BpmnStateTransitionBehavior stateTransitionBehavior;
  private SuspensionMetrics suspensionMetrics;
  private SuspensionState suspensionState;
  private StateWriter stateWriter;
  private BpmnElementContext context;
  private ExecutableProcess element;
  private ProcessProcessor processor;

  @BeforeEach
  void setUp() {
    final var bpmnBehaviors = mock(BpmnBehaviors.class);
    when(bpmnBehaviors.processResultSenderBehavior())
        .thenReturn(mock(BpmnProcessResultSenderBehavior.class));
    when(bpmnBehaviors.bufferedMessageStartEventBehavior())
        .thenReturn(mock(BpmnBufferedMessageStartEventBehavior.class));
    when(bpmnBehaviors.processDeletionBehavior())
        .thenReturn(mock(BpmnProcessDeletionBehavior.class));
    when(bpmnBehaviors.agentInstanceBehavior()).thenReturn(mock(AgentInstanceBehavior.class));
    when(bpmnBehaviors.eventSubscriptionBehavior())
        .thenReturn(mock(BpmnEventSubscriptionBehavior.class));
    when(bpmnBehaviors.compensationSubscriptionBehaviour())
        .thenReturn(mock(BpmnCompensationSubscriptionBehaviour.class));
    when(bpmnBehaviors.incidentBehavior()).thenReturn(mock(BpmnIncidentBehavior.class));
    when(bpmnBehaviors.jobBehavior()).thenReturn(mock(BpmnJobBehavior.class));

    stateTransitionBehavior = mock(BpmnStateTransitionBehavior.class);
    suspensionMetrics = mock(SuspensionMetrics.class);
    final var asyncRequestState = mock(AsyncRequestState.class);
    when(asyncRequestState.findRequest(anyLong(), any(), any())).thenReturn(Optional.empty());
    suspensionState = mock(SuspensionState.class);
    stateWriter = mock(StateWriter.class);

    processor =
        new ProcessProcessor(
            bpmnBehaviors,
            stateTransitionBehavior,
            asyncRequestState,
            suspensionState,
            suspensionMetrics,
            stateWriter);

    element = mock(ExecutableProcess.class);
    context = mock(BpmnElementContext.class);
    when(context.getElementInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(context.getParentProcessInstanceKey()).thenReturn(0L);
    when(context.getRecordValue()).thenReturn(new ProcessInstanceRecord());
    when(stateTransitionBehavior.transitionToCompleted(any(), any()))
        .thenReturn(Either.right(context));
    when(stateTransitionBehavior.transitionToTerminated(any(), any(), any())).thenReturn(context);
    when(stateTransitionBehavior.terminateChildInstances(any())).thenReturn(true);
  }

  @Test
  void shouldDiscardDurationsOnCompletion() {
    // given — a process instance completing normally (whether or not it was ever resuming: a
    // buffered command can drain straight to completion, superseding the RESUME_JOBS/
    // COMPLETE_RESUMING chain before it reaches the normal resume finish line)

    // when
    processor.finalizeCompletion(element, context);

    // then — in-flight suspend/resume measurements, if any, are discarded rather than left dangling
    verify(suspensionMetrics).discardDurations(PROCESS_INSTANCE_KEY);
  }

  @Test
  void shouldWriteResumedWhenTerminatingWhileResuming() {
    // given
    when(suspensionState.getSuspensionState(PROCESS_INSTANCE_KEY))
        .thenReturn(SuspensionState.State.RESUMING);

    // when
    processor.onTerminate(element, context);

    // then
    verify(stateWriter)
        .appendFollowUpEvent(eq(PROCESS_INSTANCE_KEY), eq(ProcessInstanceIntent.RESUMED), any());
    verify(suspensionMetrics).instanceResumed();
  }

  @Test
  void shouldNotWriteResumedWhenTerminatingWhileSuspended() {
    // given - no resume was requested, so there is nothing to finish
    when(suspensionState.getSuspensionState(PROCESS_INSTANCE_KEY))
        .thenReturn(SuspensionState.State.SUSPENDED);

    // when
    processor.onTerminate(element, context);

    // then
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    verify(suspensionMetrics, never()).instanceResumed();
  }
}
