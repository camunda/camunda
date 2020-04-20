/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.multiinstance;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.ExpressionProcessor;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMultiInstanceBody;
import io.zeebe.engine.processor.workflow.handlers.CatchEventSubscriber;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.List;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;

public final class MultiInstanceBodyCompletingHandler extends AbstractMultiInstanceBodyHandler {

  private final CatchEventSubscriber catchEventSubscriber;

  public MultiInstanceBodyCompletingHandler(
      final Consumer<BpmnStepContext<?>> innerHandler,
      final CatchEventSubscriber catchEventSubscriber,
      final ExpressionProcessor expressionProcessor) {
    super(WorkflowInstanceIntent.ELEMENT_COMPLETED, innerHandler, expressionProcessor);
    this.catchEventSubscriber = catchEventSubscriber;
  }

  @Override
  protected boolean handleMultiInstanceBody(
      final BpmnStepContext<ExecutableMultiInstanceBody> context) {
    catchEventSubscriber.unsubscribeFromEvents(context);

    context
        .getElement()
        .getLoopCharacteristics()
        .getOutputCollection()
        .ifPresent(variableName -> propagateVariable(context, variableName));

    return true;
  }

  private void propagateVariable(
      final BpmnStepContext<ExecutableMultiInstanceBody> context, final DirectBuffer variableName) {
    final var variablesState = context.getElementInstanceState().getVariablesState();

    final var sourceScope = context.getKey();
    final var targetScope = context.getFlowScopeInstance().getKey();
    final var workflowKey = context.getValue().getWorkflowKey();

    final var document = variablesState.getVariablesAsDocument(sourceScope, List.of(variableName));

    variablesState.setVariablesFromDocument(targetScope, workflowKey, document);
  }
}
