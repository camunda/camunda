/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn.behavior;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.common.ErrorEventHandler;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.ElementInstanceState;
import org.agrona.DirectBuffer;

public final class BpmnEventPublicationBehavior {

  private final ErrorEventHandler errorEventHandler;
  private final TypedStreamWriter streamWriter;
  private final ElementInstanceState elementInstanceState;

  public BpmnEventPublicationBehavior(
      final ZeebeState zeebeState, final TypedStreamWriter streamWriter) {
    final var workflowState = zeebeState.getWorkflowState();
    final var keyGenerator = zeebeState.getKeyGenerator();
    elementInstanceState = workflowState.getElementInstanceState();
    errorEventHandler = new ErrorEventHandler(workflowState, keyGenerator);
    this.streamWriter = streamWriter;
  }

  /**
   * Throws an error event that must be caught somewhere in the scope hierarchy.
   *
   * @return {@code true} if the error event is thrown and caught by an catch event
   * @see ErrorEventHandler#throwErrorEvent(DirectBuffer, ElementInstance, TypedStreamWriter)
   */
  public boolean throwErrorEvent(final DirectBuffer errorCode, final BpmnElementContext context) {
    final var flowScopeInstance = elementInstanceState.getInstance(context.getFlowScopeKey());
    return errorEventHandler.throwErrorEvent(errorCode, flowScopeInstance, streamWriter);
  }
}
