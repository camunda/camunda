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
import io.zeebe.engine.state.instance.IncidentState;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ElementTerminatingHandlerTest extends ElementHandlerTestCase<ExecutableFlowNode> {
  private final IncidentState incidentState = zeebeStateRule.getZeebeState().getIncidentState();
  private ElementTerminatingHandler<ExecutableFlowNode> handler;

  @Override
  @Before
  public void setUp() {
    super.setUp();
    handler = new ElementTerminatingHandler<>();
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
        createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_TERMINATING);
    instance.setState(WorkflowInstanceIntent.ELEMENT_COMPLETING);
    elementInstanceState.updateInstance(instance);

    // when - then
    Assertions.assertThat(handler.shouldHandleState(context)).isFalse();
  }
}
