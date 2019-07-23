/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.element;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.handlers.AbstractHandler;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

/**
 * Resolves any incident that were associated with this element.
 *
 * @param <T>
 */
public class ElementTerminatingHandler<T extends ExecutableFlowNode> extends AbstractHandler<T> {
  public ElementTerminatingHandler() {
    this(WorkflowInstanceIntent.ELEMENT_TERMINATED);
  }

  public ElementTerminatingHandler(WorkflowInstanceIntent nextState) {
    super(nextState);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    return true;
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<T> context) {
    return super.shouldHandleState(context) && isStateSameAsElementState(context);
  }
}
