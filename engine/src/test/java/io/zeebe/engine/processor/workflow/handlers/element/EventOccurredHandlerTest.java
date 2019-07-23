/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.element;

import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.EnumSet;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class EventOccurredHandlerTest extends ElementHandlerTestCase<ExecutableFlowNode> {
  private EventOccurredHandler<ExecutableFlowNode> handler;

  @Override
  @Before
  public void setUp() {
    super.setUp();
    handler = new EventOccurredHandler<>();
  }

  @Override
  protected ElementInstance createAndSetContextElementInstance(WorkflowInstanceIntent state) {
    final ElementInstance instance = super.createAndSetContextElementInstance(state);
    return instance;
  }

  @Test
  public void shouldNotHandleStateIfNoElementGiven() {
    // given
    final ElementInstance instance =
        createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    instance.getValue().setWorkflowInstanceKey(zeebeStateRule.getKeyGenerator().nextKey());
    zeebeStateRule
        .getZeebeState()
        .getWorkflowState()
        .getElementInstanceState()
        .removeInstance(instance.getKey());

    // when - then
    Assertions.assertThat(handler.shouldHandleState(context)).isFalse();
  }

  @Test
  public void shouldNotHandleStateIfElementIsNotActive() {
    // given
    final Set<WorkflowInstanceIntent> inactiveStates =
        EnumSet.complementOf(EnumSet.of(WorkflowInstanceIntent.ELEMENT_ACTIVATED));

    // when - then
    for (final WorkflowInstanceIntent inactiveState : inactiveStates) {
      final ElementInstance instance = createAndSetContextElementInstance(inactiveState);
      instance.getValue().setWorkflowInstanceKey(zeebeStateRule.getKeyGenerator().nextKey());

      Assertions.assertThat(handler.shouldHandleState(context)).isFalse();
    }
  }

  @Test
  public void shouldHandleStateIfElementHasNoWorkflowInstance() {
    // given
    final Set<WorkflowInstanceIntent> inactiveStates =
        EnumSet.complementOf(EnumSet.of(WorkflowInstanceIntent.ELEMENT_ACTIVATED));

    // when - then
    for (final WorkflowInstanceIntent inactiveState : inactiveStates) {
      createAndSetContextElementInstance(inactiveState);

      Assertions.assertThat(handler.shouldHandleState(context)).isTrue();
    }
  }

  @Test
  public void shouldHandleStateIfElementIsActive() {
    // given
    createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when - then
    Assertions.assertThat(handler.shouldHandleState(context)).isTrue();
  }
}
