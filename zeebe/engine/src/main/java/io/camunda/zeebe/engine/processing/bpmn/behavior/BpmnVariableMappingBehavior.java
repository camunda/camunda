/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.EventTrigger;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class BpmnVariableMappingBehavior {
  private final ExpressionProcessor expressionProcessor;
  private final VariableState variablesState;
  private final ElementInstanceState elementInstanceState;
  private final VariableBehavior variableBehavior;
  private final EventScopeInstanceState eventScopeInstanceState;

  private final EventTriggerBehavior eventTriggerBehavior;

  public BpmnVariableMappingBehavior(
      final ExpressionProcessor expressionProcessor,
      final ProcessingState processingState,
      final VariableBehavior variableBehavior,
      final EventTriggerBehavior eventTriggerBehavior) {
    this.expressionProcessor = expressionProcessor;
    elementInstanceState = processingState.getElementInstanceState();
    variablesState = processingState.getVariableState();
    this.variableBehavior = variableBehavior;
    eventScopeInstanceState = processingState.getEventScopeInstanceState();
    this.eventTriggerBehavior = eventTriggerBehavior;
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
    final DirectBuffer bpmnProcessId = context.getBpmnProcessId();
    final Optional<Expression> inputMappingExpression = element.getInputMappings();

    if (inputMappingExpression.isPresent()) {
      return expressionProcessor
          .evaluateVariableMappingExpression(inputMappingExpression.get(), scopeKey)
          .map(
              result -> {
                variableBehavior.mergeLocalDocument(
                    scopeKey,
                    processDefinitionKey,
                    processInstanceKey,
                    bpmnProcessId,
                    context.getTenantId(),
                    result);
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
    final DirectBuffer bpmnProcessId = context.getBpmnProcessId();
    final long scopeKey = getVariableScopeKey(context);
    final Optional<Expression> outputMappingExpression = element.getOutputMappings();

    final EventTrigger eventTrigger = eventScopeInstanceState.peekEventTrigger(elementInstanceKey);
    boolean hasVariables = false;
    DirectBuffer variables = null;

    if (eventTrigger != null) {
      variables = eventTrigger.getVariables();
      hasVariables = variables.capacity() > 0;

      eventTriggerBehavior.processEventTriggered(
          eventTrigger.getEventKey(),
          processDefinitionKey,
          processInstanceKey,
          context.getTenantId(),
          elementInstanceKey,
          element.getId());
    }

    if (outputMappingExpression.isPresent()) {
      // set as local variables
      if (hasVariables) {
        variableBehavior.mergeLocalDocument(
            elementInstanceKey,
            processDefinitionKey,
            processInstanceKey,
            bpmnProcessId,
            context.getTenantId(),
            variables);
      }

      // apply the output mappings
      return expressionProcessor
          .evaluateVariableMappingExpression(outputMappingExpression.get(), elementInstanceKey)
          .map(
              result -> {
                variableBehavior.mergeDocument(
                    scopeKey,
                    processDefinitionKey,
                    processInstanceKey,
                    bpmnProcessId,
                    context.getTenantId(),
                    result);
                return null;
              });

    } else if (hasVariables) {
      // merge/propagate the event variables by default
      variableBehavior.mergeDocument(
          elementInstanceKey,
          processDefinitionKey,
          processInstanceKey,
          bpmnProcessId,
          context.getTenantId(),
          variables);
    } else if (isConnectedToEventBasedGateway(element)
        || (element.getElementType() == BpmnElementType.BOUNDARY_EVENT && !isErrorEvent(element))
        || element.getElementType() == BpmnElementType.START_EVENT) {
      // event variables are set local variables instead of temporary variables
      final var localVariables = variablesState.getVariablesLocalAsDocument(elementInstanceKey);
      variableBehavior.mergeDocument(
          scopeKey,
          processDefinitionKey,
          processInstanceKey,
          bpmnProcessId,
          context.getTenantId(),
          localVariables);
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

  private boolean isConnectedToEventBasedGateway(final ExecutableFlowNode element) {
    if (element instanceof final ExecutableCatchEventElement catchEvent) {
      return catchEvent.isConnectedToEventBasedGateway();
    } else {
      return false;
    }
  }

  private boolean isErrorEvent(final ExecutableFlowNode element) {
    if (element instanceof final ExecutableCatchEventElement catchEvent) {
      return catchEvent.isError();
    } else {
      return false;
    }
  }
}
