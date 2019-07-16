/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.element;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ElementCompletedHandlerTest extends ElementHandlerTestCase<ExecutableFlowNode> {
  private ElementCompletedHandler<ExecutableFlowNode> handler;

  @Override
  @Before
  public void setUp() {
    super.setUp();
    handler = new ElementCompletedHandler<>();
  }

  @Test
  public void shouldNotHandleStateIfNoElementGiven() {
    // given

    // when - then
    Assertions.assertThat(handler.shouldHandleState(context)).isFalse();
  }

  @Test
  public void shouldNotHandleStateIfInMemoryStateIsDifferent() {
    // given
    final ElementInstance instance =
        createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_COMPLETED);
    instance.setState(WorkflowInstanceIntent.ELEMENT_TERMINATED);
    elementInstanceState.updateInstance(instance);

    // when - then
    Assertions.assertThat(handler.shouldHandleState(context)).isFalse();
  }

  @Test
  public void shouldNotHandleStateIfFlowScopeIsNotActivated() {
    // given
    final ElementInstance flowScope = newElementInstance(WorkflowInstanceIntent.ELEMENT_TERMINATED);
    createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_COMPLETED, flowScope);

    // when - then
    Assertions.assertThat(handler.shouldHandleState(context)).isFalse();
  }

  @Test
  public void shouldHandleStateIfElementIsRootFlowScope() {
    // given
    createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_COMPLETED);

    // when - then
    Assertions.assertThat(handler.shouldHandleState(context)).isTrue();
  }

  @Test
  public void shouldCompleteFlowScopeIfLastActiveElement() {
    // given
    final ElementInstance flowScope = newElementInstance(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_COMPLETED, flowScope);

    // when
    handler.handleState(context);

    // then
    verify(eventOutput, times(1))
        .appendFollowUpEvent(
            flowScope.getKey(), WorkflowInstanceIntent.ELEMENT_COMPLETING, flowScope.getValue());
  }

  @Test
  public void shouldNotCompleteFlowScopeIfNonePresent() {
    // given
    createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_COMPLETED);

    // when
    handler.handleState(context);

    // then
    verify(eventOutput, never())
        .appendFollowUpEvent(
            anyLong(), any(WorkflowInstanceIntent.class), any(WorkflowInstanceRecord.class));
  }

  @Test
  public void shouldNotCompleteFlowScopeIfMoreActiveElementsPresent() {
    // given
    final ElementInstance flowScope = newElementInstance(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_COMPLETED, flowScope);
    newElementInstance(WorkflowInstanceIntent.ELEMENT_ACTIVATED, flowScope);

    // when
    handler.handleState(context);

    // then
    verify(eventOutput, never())
        .appendFollowUpEvent(
            ArgumentMatchers.eq(flowScope.getKey()),
            any(WorkflowInstanceIntent.class),
            ArgumentMatchers.eq(flowScope.getValue()));
  }
}
