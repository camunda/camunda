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
import io.camunda.zeebe.engine.processing.deployment.model.element.InputMapping;
import io.camunda.zeebe.engine.processing.deployment.model.element.InputMappings;
import io.camunda.zeebe.engine.processing.deployment.model.element.OutputMapping;
import io.camunda.zeebe.engine.processing.variable.InputMappingResultBuilder;
import io.camunda.zeebe.engine.processing.variable.MsgPackPath;
import io.camunda.zeebe.engine.processing.variable.OutputMappingResultBuilder;
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
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
  private final boolean evaluateDuplicateOutputMappingTargetsInOrder;

  public BpmnVariableMappingBehavior(
      final ExpressionProcessor expressionProcessor,
      final ProcessingState processingState,
      final VariableBehavior variableBehavior,
      final EventTriggerBehavior eventTriggerBehavior,
      final boolean evaluateDuplicateOutputMappingTargetsInOrder) {
    this.expressionProcessor = expressionProcessor;
    inputMappingExpressionProcessor = expressionProcessor.withSecretReferenceContext();
    elementInstanceState = processingState.getElementInstanceState();
    variablesState = processingState.getVariableState();
    this.variableBehavior = variableBehavior;
    eventScopeInstanceState = processingState.getEventScopeInstanceState();
    this.eventTriggerBehavior = eventTriggerBehavior;
    this.evaluateDuplicateOutputMappingTargetsInOrder =
        evaluateDuplicateOutputMappingTargetsInOrder;
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
    final long scopeKey = context.getElementInstanceKey();
    final String tenantId = context.getTenantId();
    final Optional<InputMappings> inputMappings = element.getInputMappings();

    if (inputMappings.isEmpty()) {
      return Either.right(null);
    }

    final var resultBuilder =
        new InputMappingResultBuilder(
            name -> variablesState.getVariable(scopeKey, BufferUtil.wrapString(name)));
    // secret references (camunda.secrets.<name>) are resolved to their placeholder string only
    // for input mappings, so a modeled reference survives evaluation instead of nulling
    final var processor =
        inputMappingExpressionProcessor.prependContext(
            name -> Either.left(resultBuilder.getVariable(name)));

    for (final InputMapping mapping : inputMappings.get().mappings()) {
      final var result =
          processor.evaluateVariableMappingExpression(mapping.source(), scopeKey, tenantId);
      if (result.isLeft()) {
        return Either.left(result.getLeft());
      }
      resultBuilder.put(mapping.targetPath(), result.get());
    }
    return mapLocalVariables(context, element, resultBuilder.toDocument());
  }

  /**
   * Apply the output mappings for a BPMN element. Generally called on completing of the element.
   *
   * <p>The mappings are evaluated one by one in modeling order. Each mapping's source expression
   * sees the results of the earlier mappings (they take priority over same-named scope variables)
   * and falls back to the element's variable scope otherwise. A nested target merges with the
   * existing scope value at every path level. Evaluation stops at the first failing mapping and no
   * variables are applied in that case.
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
    final int storageOrdinalKey = context.getStorageOrdinalKey();
    final String tenantId = context.getTenantId();
    final Optional<List<OutputMapping>> outputMappings = element.getOutputMappings();

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
          storageOrdinalKey,
          tenantId,
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

      // Resolves the current scope value at a nested target's path so the builder can merge into
      // it and keep the existing sibling properties: look up the top-level variable in the element
      // scope, then navigate into it along the remaining path segments (null when absent).
      final var resultBuilder =
          new OutputMappingResultBuilder(
              path ->
                  Optional.ofNullable(
                          variablesState.getVariable(
                              elementInstanceKey, BufferUtil.wrapString(path.getFirst())))
                      .map(rootValue -> MsgPackPath.navigate(rootValue, path, 1))
                      .orElse(null));
      final var processor =
          expressionProcessor.prependContext(name -> Either.left(resultBuilder.getVariable(name)));

      final var mappingsToEvaluate =
          evaluateDuplicateOutputMappingTargetsInOrder
              ? outputMappings.get()
              : withoutSupersededDuplicateTargets(outputMappings.get());

      for (final OutputMapping mapping : mappingsToEvaluate) {
        final var result =
            processor.evaluateVariableMappingExpression(
                mapping.source(), elementInstanceKey, tenantId);
        if (result.isLeft()) {
          return Either.left(result.getLeft());
        }
        resultBuilder.put(mapping.targetPath(), result.get());
      }
      return mapVariables(
          context, element, getVariableScopeKey(context), resultBuilder.toDocument());

    } else if (hasVariables) {
      // merge/propagate the event variables by default
      final Either<Failure, Void> variableEither =
          mapVariables(context, element, elementInstanceKey, variables);
      if (variableEither.isLeft()) {
        return variableEither;
      }
    } else if (isConnectedToEventBasedGateway(element)
        || (element.getElementType() == BpmnElementType.BOUNDARY_EVENT && !isErrorEvent(element))
        || element.getElementType() == BpmnElementType.START_EVENT) {
      // event variables are set local variables instead of temporary variables
      final var localVariables = variablesState.getVariablesLocalAsDocument(elementInstanceKey);
      final Either<Failure, Void> variableEither =
          mapVariables(context, element, getVariableScopeKey(context), localVariables);
      if (variableEither.isLeft()) {
        return variableEither;
      }
    }
    return Either.right(null);
  }

  private @NonNull Either<Failure, Void> mapVariables(
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
          context.getStorageOrdinalKey(),
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
          context.getStorageOrdinalKey(),
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

  /**
   * Reproduces the target-collision handling of the removed combined-FEEL-context builder, for the
   * {@code evaluateDuplicateOutputMappingTargetsInOrder} kill-switch: a mapping whose target path
   * collides with an earlier one (equal, or one a prefix of the other) replaces it outright,
   * keeping the FIRST colliding mapping's position but the LAST one's value -- mirroring how
   * re-inserting an existing key into a {@code LinkedHashMap} keeps its iteration position but
   * replaces its value. The superseded mapping's source is dropped entirely and never evaluated.
   */
  static List<OutputMapping> withoutSupersededDuplicateTargets(final List<OutputMapping> mappings) {
    record Survivor(int firstIndex, OutputMapping mapping) {}

    final var survivors = new ArrayList<Survivor>();
    for (int i = 0; i < mappings.size(); i++) {
      final var mapping = mappings.get(i);
      var firstIndex = i;
      final var iterator = survivors.iterator();
      while (iterator.hasNext()) {
        final var existing = iterator.next();
        if (collides(existing.mapping().targetPath(), mapping.targetPath())) {
          firstIndex = Math.min(firstIndex, existing.firstIndex());
          iterator.remove();
        }
      }
      survivors.add(new Survivor(firstIndex, mapping));
    }
    survivors.sort(Comparator.comparingInt(Survivor::firstIndex));
    return survivors.stream().map(Survivor::mapping).toList();
  }

  private static boolean collides(final List<String> a, final List<String> b) {
    final var shorter = a.size() <= b.size() ? a : b;
    final var longer = a.size() <= b.size() ? b : a;
    return longer.subList(0, shorter.size()).equals(shorter);
  }
}
