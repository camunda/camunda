/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers;

import io.zeebe.el.Expression;
import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.ExpressionProcessor;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class IOMappingHelper {

  private final ExpressionProcessor expressionProcessor;

  public IOMappingHelper(final ExpressionProcessor expressionProcessor) {
    this.expressionProcessor = expressionProcessor;
  }

  public <T extends ExecutableFlowNode> boolean applyOutputMappings(
      final BpmnStepContext<T> context) {
    final VariablesState variablesState = context.getElementInstanceState().getVariablesState();

    final T element = context.getElement();
    final WorkflowInstanceRecord record = context.getValue();
    final long elementInstanceKey = context.getKey();
    final long workflowKey = record.getWorkflowKey();
    final Optional<Expression> outputMappingExpression = element.getOutputMappings();
    final var hasOutputMappings = outputMappingExpression.isPresent();

    final DirectBuffer temporaryVariables =
        variablesState.getTemporaryVariables(elementInstanceKey);
    if (temporaryVariables != null) {
      if (hasOutputMappings) {
        variablesState.setVariablesLocalFromDocument(
            elementInstanceKey, workflowKey, temporaryVariables);
      } else {
        variablesState.setVariablesFromDocument(
            elementInstanceKey, workflowKey, temporaryVariables);
      }

      variablesState.removeTemporaryVariables(elementInstanceKey);
    }

    final var expressionResult =
        outputMappingExpression.flatMap(
            expression ->
                expressionProcessor.evaluateVariableMappingExpression(expression, context));

    expressionResult.ifPresent(
        resultVariables ->
            variablesState.setVariablesFromDocument(
                getVariableScopeKey(context), workflowKey, resultVariables));

    return !hasOutputMappings || expressionResult.isPresent();
  }

  public <T extends ExecutableFlowNode> boolean applyInputMappings(
      final BpmnStepContext<T> context) {

    final T element = context.getElement();
    final Optional<Expression> inputMappingExpression = element.getInputMappings();
    final boolean hasInputMappings = inputMappingExpression.isPresent();

    final var expressionResult =
        inputMappingExpression.flatMap(
            expression ->
                expressionProcessor.evaluateVariableMappingExpression(expression, context));

    expressionResult.ifPresent(
        result -> {
          final long scopeKey = context.getKey();
          final long workflowKey = context.getValue().getWorkflowKey();
          context
              .getElementInstanceState()
              .getVariablesState()
              .setVariablesLocalFromDocument(scopeKey, workflowKey, result);
        });

    return !hasInputMappings || expressionResult.isPresent();
  }

  private long getVariableScopeKey(final BpmnStepContext<?> context) {
    final var elementInstanceKey = context.getKey();
    final var flowScopeKey = context.getValue().getFlowScopeKey();

    // an inner multi-instance activity needs to read from/write to its own scope
    // to access the input and output element variables
    final var isMultiInstanceActivity =
        context.getElementInstance().getMultiInstanceLoopCounter() > 0;
    return isMultiInstanceActivity ? elementInstanceKey : flowScopeKey;
  }
}
