/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.element;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.ExpressionProcessor;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.handlers.AbstractHandler;
import io.zeebe.engine.processor.workflow.handlers.IOMappingHelper;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

/**
 * Applies input mappings in the scope.
 *
 * @param <T>
 */
public class ElementActivatingHandler<T extends ExecutableFlowNode> extends AbstractHandler<T> {
  private final IOMappingHelper ioMappingHelper;

  public ElementActivatingHandler(final ExpressionProcessor expressionProcessor) {
    this(WorkflowInstanceIntent.ELEMENT_ACTIVATED, expressionProcessor);
  }

  public ElementActivatingHandler(
      final WorkflowInstanceIntent nextState, final ExpressionProcessor expressionProcessor) {
    this(nextState, new IOMappingHelper(expressionProcessor));
  }

  public ElementActivatingHandler(
      final WorkflowInstanceIntent nextState, final IOMappingHelper ioMappingHelper) {
    super(nextState);
    this.ioMappingHelper = ioMappingHelper;
  }

  @Override
  protected boolean handleState(final BpmnStepContext<T> context) {
    return ioMappingHelper.applyInputMappings(context);
  }

  @Override
  protected boolean shouldHandleState(final BpmnStepContext<T> context) {
    return super.shouldHandleState(context)
        && isStateSameAsElementState(context)
        && (isRootScope(context) || isElementActive(context.getFlowScopeInstance()))
        && !isElementInterrupted(context);
  }
}
