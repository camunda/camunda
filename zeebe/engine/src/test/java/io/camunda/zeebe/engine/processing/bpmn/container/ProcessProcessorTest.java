/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.container;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.metrics.SuspensionMetrics;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBufferedMessageStartEventBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnProcessDeletionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnProcessResultSenderBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.state.immutable.AsyncRequestState;
import io.camunda.zeebe.engine.state.immutable.SuspensionState;
import io.camunda.zeebe.util.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class ProcessProcessorTest {

  private static final long PROCESS_INSTANCE_KEY = 42L;

  private BpmnStateTransitionBehavior stateTransitionBehavior;
  private SuspensionMetrics suspensionMetrics;
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

    stateTransitionBehavior = mock(BpmnStateTransitionBehavior.class);
    suspensionMetrics = mock(SuspensionMetrics.class);
    final var asyncRequestState = mock(AsyncRequestState.class);
    final var suspensionState = mock(SuspensionState.class);

    processor =
        new ProcessProcessor(
            bpmnBehaviors,
            stateTransitionBehavior,
            asyncRequestState,
            suspensionState,
            suspensionMetrics);

    element = mock(ExecutableProcess.class);
    context = mock(BpmnElementContext.class);
    when(context.getElementInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);
    when(context.getParentProcessInstanceKey()).thenReturn(0L);
    when(stateTransitionBehavior.transitionToCompleted(any(), any()))
        .thenReturn(Either.right(context));
  }

  @Test
  void shouldCancelResumeDurationSampleOnCompletion() {
    // given — a process instance completing normally (whether or not it was ever resuming: a
    // buffered command can drain straight to completion, superseding the RESUME_JOBS/
    // COMPLETE_RESUMING chain before it reaches the normal resume finish line)

    // when
    processor.finalizeCompletion(element, context);

    // then — the in-flight resume-duration sample, if any, is discarded rather than left dangling
    verify(suspensionMetrics).cancelResumeDuration(PROCESS_INSTANCE_KEY);
  }
}
