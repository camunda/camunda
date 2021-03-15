/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.behavior;

import io.zeebe.el.Expression;
import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processing.variable.VariableBehavior;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.mutable.MutableVariableState;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.util.Either;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class BpmnVariableMappingBehavior {
  private final ExpressionProcessor expressionProcessor;
  private final MutableVariableState variablesState;
  private final ElementInstanceState elementInstanceState;
  private final VariableBehavior variableBehavior;

  public BpmnVariableMappingBehavior(
      final ExpressionProcessor expressionProcessor,
      final MutableZeebeState zeebeState,
      final VariableBehavior variableBehavior) {
    this.expressionProcessor = expressionProcessor;
    elementInstanceState = zeebeState.getElementInstanceState();
    variablesState = zeebeState.getVariableState();
    this.variableBehavior = variableBehavior;
  }

  /**
   * Apply the input mappings for a BPMN element. Generally called on activating of the element.
   *
   * @param context The current bpmn element context
   * @param element The current bpmn element
   * @return either void if successful, otherwise a failure
   */
  public Either<Failure, Void> applyInputMappings(
      final BpmnElementContext context, final ExecutableFlowNode element) {
    final long scopeKey = context.getElementInstanceKey();
    final long processDefinitionKey = context.getProcessDefinitionKey();
    final long processInstanceKey = context.getProcessInstanceKey();
    final Optional<Expression> inputMappingExpression = element.getInputMappings();
    if (inputMappingExpression.isPresent()) {
      return expressionProcessor
          .evaluateVariableMappingExpression(inputMappingExpression.get(), scopeKey)
          .map(
              result -> {
                variableBehavior.mergeLocalDocument(
                    scopeKey, processDefinitionKey, processInstanceKey, result);
                return null;
              });
    }
    return Either.right(null);
  }

  /**
   * Apply the output mappings for a BPMN element. Generally called on completing of the element.
   *
   * @param context The current bpmn element context
   * @param element The current bpmn element
   * @return either void if successful, otherwise a failure
   */
  public Either<Failure, Void> applyOutputMappings(
      final BpmnElementContext context, final ExecutableFlowNode element) {
    final ProcessInstanceRecord record = context.getRecordValue();
    final long elementInstanceKey = context.getElementInstanceKey();
    final long processDefinitionKey = record.getProcessDefinitionKey();
    final long processInstanceKey = record.getProcessInstanceKey();
    final Optional<Expression> outputMappingExpression = element.getOutputMappings();

    // set variables
    final DirectBuffer temporaryVariables =
        variablesState.getTemporaryVariables(elementInstanceKey);
    if (temporaryVariables != null) {
      outputMappingExpression.ifPresentOrElse(
          expression ->
              variableBehavior.mergeLocalDocument(
                  elementInstanceKey, processDefinitionKey, processInstanceKey, temporaryVariables),
          () ->
              variableBehavior.mergeDocument(
                  elementInstanceKey,
                  processDefinitionKey,
                  processInstanceKey,
                  temporaryVariables));
      variablesState.removeTemporaryVariables(elementInstanceKey);
    }

    if (outputMappingExpression.isPresent()) {
      final long scopeKey = getVariableScopeKey(context);
      return expressionProcessor
          .evaluateVariableMappingExpression(outputMappingExpression.get(), elementInstanceKey)
          .map(
              result -> {
                variableBehavior.mergeDocument(
                    scopeKey, processDefinitionKey, processInstanceKey, result);
                return null;
              });
    }
    return Either.right(null);
  }

  private long getVariableScopeKey(final BpmnElementContext context) {
    final var elementInstanceKey = context.getElementInstanceKey();

    // an inner multi-instance activity needs to read from/write to its own scope
    // to access the input and output element variables
    final var isMultiInstanceActivity =
        elementInstanceState.getInstance(elementInstanceKey).getMultiInstanceLoopCounter() > 0;
    return isMultiInstanceActivity ? elementInstanceKey : context.getFlowScopeKey();
  }
}
