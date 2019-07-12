/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.element;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.handlers.IOMappingHelper;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.msgpack.mapping.MappingException;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ElementCompletingHandlerTest extends ElementHandlerTestCase<ExecutableFlowNode> {
  @Mock public IOMappingHelper ioMappingHelper;

  private ElementCompletingHandler<ExecutableFlowNode> handler;

  @Override
  @Before
  public void setUp() {
    super.setUp();
    handler = new ElementCompletingHandler<>(ioMappingHelper);
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
        createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_COMPLETING);
    instance.setState(WorkflowInstanceIntent.ELEMENT_TERMINATED);
    elementInstanceState.updateInstance(instance);

    // when - then
    Assertions.assertThat(handler.shouldHandleState(context)).isFalse();
  }

  @Test
  public void shouldNotHandleStateIfFlowScopeIsNotActivated() {
    // given
    final ElementInstance flowScope = newElementInstance(WorkflowInstanceIntent.ELEMENT_TERMINATED);
    createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_COMPLETING, flowScope);

    // when - then
    Assertions.assertThat(handler.shouldHandleState(context)).isFalse();
  }

  @Test
  public void shouldHandleStateIfElementIsRootFlowScope() {
    // given
    createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_COMPLETING);

    // when - then
    Assertions.assertThat(handler.shouldHandleState(context)).isTrue();
  }

  @Test
  public void shouldApplyOutputMappings() {
    // given
    createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_COMPLETING);

    // when
    handler.handleState(context);

    // then
    verify(ioMappingHelper, times(1)).applyOutputMappings(context);
  }

  @Test
  public void shouldRaiseIncidentOnMappingException() {
    // given
    createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_COMPLETING);
    final MappingException exception = new MappingException("failed");

    // when
    doThrow(exception).when(ioMappingHelper).applyOutputMappings(context);
    final boolean handled = handler.handleState(context);

    // then
    assertThat(handled).isFalse();
    verifyIncidentRaised();
  }

  @Test
  public void shouldHandleState() {
    // given
    createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_COMPLETING);

    // when
    final boolean handled = handler.handleState(context);

    // then
    assertThat(handled).isTrue();
  }
}
