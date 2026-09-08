/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.common.ValidationException;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.InputMappings;
import io.camunda.zeebe.engine.processing.deployment.model.element.OutputMappings;
import io.camunda.zeebe.engine.processing.variable.MappingContext;
import io.camunda.zeebe.engine.processing.variable.MappingExpressionProcessor;
import io.camunda.zeebe.engine.processing.variable.MappingResolver;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.instance.EventTrigger;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NonNull;

public final class BpmnVariableMappingBehavior {
  private final ExpressionProcessor expressionProcessor;
  // secret-aware variant used only for input mappings; stateless over a fixed delegate, so it is
  // built once instead of per activation
  private final ExpressionProcessor inputMappingExpressionProcessor;
  private final VariableState variablesState;
  private final ElementInstanceState elementInstanceState;
  private final VariableBehavior variableBehavior;
  private final EventScopeInstanceState eventScopeInstanceState;

  private final EventTriggerBehavior eventTriggerBehavior;
  private final MappingResolver<InputMappings> inputMappingResolver;
  private final MappingResolver<OutputMappings> outputMappingResolver;

  public BpmnVariableMappingBehavior(
      final ExpressionProcessor expressionProcessor,
      final ProcessingState processingState,
      final VariableBehavior variableBehavior,
      final EventTriggerBehavior eventTriggerBehavior,
      final MappingResolver<InputMappings> inputMappingResolver,
      final MappingResolver<OutputMappings> outputMappingResolver) {
    this.expressionProcessor = expressionProcessor;
    inputMappingExpressionProcessor = expressionProcessor.withSecretReferenceContext();
    elementInstanceState = processingState.getElementInstanceState();
    variablesState = processingState.getVariableState();
    this.variableBehavior = variableBehavior;
    eventScopeInstanceState = processingState.getEventScopeInstanceState();
    this.eventTriggerBehavior = eventTriggerBehavior;
    this.inputMappingResolver = inputMappingResolver;
    this.outputMappingResolver = outputMappingResolver;
  }

  /**
   * Apply the input mappings for a BPMN element. Generally called on activating of the element.
   *
   * <p>The mappings are evaluated one by one in modeling order. Each mapping's source expression
   * sees the results of the earlier mappings and falls back to the element's variable scope
   * otherwise. An earlier result at a nested target shadows a same-named scope variable only
   * partially: the keys it defined win, and the rest fall through to the scope value. An earlier
   * result assigned to a whole name shadows it totally. Evaluation stops at the first failing
   * mapping and no variables are applied in that case.
   *
   * @param context The current bpmn element context
   * @param element The current bpmn element
   * @return either void if successful, otherwise a failure
   */
  public Either<Failure, Void> applyInputMappings(
      final BpmnElementContext context, final ExecutableFlowNode element) {
    final Optional<InputMappings> inputMappings = element.getInputMappings();

    if (inputMappings.isEmpty()) {
      return Either.right(null);
    }

    // secret references (camunda.secrets.<name>) are resolved to their placeholder string only
    // for input mappings, so a modeled reference survives evaluation instead of nulling
    final var mappingContext =
        new MappingContext(
            element.getId(),
            context.getElementInstanceKey(),
            context.getProcessInstanceKey(),
            context.getProcessDefinitionKey(),
            context.getTenantId());
    final var result =
        inputMappingResolver.resolve(
            inputMappings.get(),
            new MappingExpressionProcessor(inputMappingExpressionProcessor, mappingContext));
    if (result.isLeft()) {
      return Either.left(result.getLeft());
    }
    return mapLocalVariables(context, element, result.get());
  }

  /**
   * Apply the output mappings for a BPMN element. Generally called on completing of the element.
   *
   * <p>The evaluation strategy depends on the configured {@link
   * io.camunda.zeebe.engine.EngineConfiguration.OutputMappingMode}:
   *
   * <ul>
   *   <li>{@code ORDERED} (opt-in): each mapping is evaluated in declaration order; later mappings
   *       see earlier results, and nested targets merge with the existing scope value at every path
   *       level.
   *   <li>{@code COMBINED} (default): all mappings are evaluated as a single pre-built FEEL context
   *       literal against the outer scope; no inter-mapping visibility and no nested-path scope
   *       seeding. This restores the pre-#59087 behavior.
   * </ul>
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
    final String tenantId = context.getTenantId();
    final Optional<OutputMappings> outputMappings = element.getOutputMappings();

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

    if (outputMappings.isPresent()) {
      // set as local variables
      if (hasVariables) {
        final Either<Failure, Void> variableEither = mapLocalVariables(context, element, variables);
        if (variableEither.isLeft()) {
          return variableEither;
        }
      }

      final var mappingContext =
          new MappingContext(
              element.getId(),
              elementInstanceKey,
              processInstanceKey,
              processDefinitionKey,
              tenantId);
      final var resolveResult =
          outputMappingResolver.resolve(
              outputMappings.get(),
              new MappingExpressionProcessor(expressionProcessor, mappingContext));
      if (resolveResult.isLeft()) {
        return Either.left(resolveResult.getLeft());
      }
      return propagateVariables(
          context, element, getVariableScopeKey(context), resolveResult.get());

    } else if (hasVariables) {
      // merge/propagate the event variables by default
      final Either<Failure, Void> variableEither =
          propagateVariables(context, element, elementInstanceKey, variables);
      if (variableEither.isLeft()) {
        return variableEither;
      }
    } else if (isConnectedToEventBasedGateway(element)
        || (element.getElementType() == BpmnElementType.BOUNDARY_EVENT && !isErrorEvent(element))
        || element.getElementType() == BpmnElementType.START_EVENT) {
      // event variables are set local variables instead of temporary variables
      final var localVariables = variablesState.getVariablesLocalAsDocument(elementInstanceKey);
      final Either<Failure, Void> variableEither =
          propagateVariables(context, element, getVariableScopeKey(context), localVariables);
      if (variableEither.isLeft()) {
        return variableEither;
      }
    }
    return Either.right(null);
  }

  private @NonNull Either<Failure, Void> propagateVariables(
      final BpmnElementContext context,
      final ExecutableFlowNode element,
      final long scopeKey,
      final DirectBuffer result) {
    final ProcessInstanceRecord record = context.getRecordValue();
    try {
      variableBehavior.mergeDocument(
          scopeKey,
          record.getProcessDefinitionKey(),
          record.getProcessInstanceKey(),
          context.getRootProcessInstanceKey(),
          context.getBpmnProcessId(),
          context.getTenantId(),
          result);
      return Either.right(null);
    } catch (final ValidationException e) {
      return Either.left(
          new Failure(
              String.format(
                  "Failed to merge variables for element '%s' with key '%d': %s",
                  element.getId(), context.getElementInstanceKey(), e.getMessage()),
              ErrorType.IO_MAPPING_ERROR));
    }
  }

  private @NonNull Either<Failure, Void> mapLocalVariables(
      final BpmnElementContext context,
      final ExecutableFlowNode element,
      final DirectBuffer result) {
    final ProcessInstanceRecord record = context.getRecordValue();
    try {
      variableBehavior.mergeLocalDocument(
          context.getElementInstanceKey(),
          record.getProcessDefinitionKey(),
          record.getProcessInstanceKey(),
          context.getRootProcessInstanceKey(),
          context.getBpmnProcessId(),
          context.getTenantId(),
          result);
      return Either.right(null);
    } catch (final ValidationException e) {
      return Either.left(
          new Failure(
              String.format(
                  "Failed to merge local variables for element '%s' with key '%d': %s",
                  element.getId(), context.getElementInstanceKey(), e.getMessage()),
              ErrorType.IO_MAPPING_ERROR));
    }
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
