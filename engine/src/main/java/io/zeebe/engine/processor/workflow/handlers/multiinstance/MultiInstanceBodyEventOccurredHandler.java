/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.multiinstance;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.BpmnStepHandler;
import io.zeebe.engine.processor.workflow.ExpressionProcessor;
import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMultiInstanceBody;
import java.util.function.Consumer;
import java.util.function.Function;

public final class MultiInstanceBodyEventOccurredHandler extends AbstractMultiInstanceBodyHandler {

  private final BpmnStepHandler eventHandler;

  public MultiInstanceBodyEventOccurredHandler(
      final Function<BpmnStep, BpmnStepHandler> handlerLookup,
      final Consumer<BpmnStepContext<?>> innerHandler,
      final ExpressionProcessor expressionProcessor) {
    super(null, innerHandler, expressionProcessor);

    eventHandler = handlerLookup.apply(BpmnStep.ACTIVITY_EVENT_OCCURRED);
  }

  @Override
  protected boolean shouldHandleState(final BpmnStepContext<ExecutableMultiInstanceBody> context) {
    return isElementActive(context.getFlowScopeInstance());
  }

  @Override
  protected boolean handleMultiInstanceBody(
      final BpmnStepContext<ExecutableMultiInstanceBody> context) {
    eventHandler.handle(context);
    return true;
  }
}
