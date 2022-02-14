/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.dmn.DecisionEngine;
import io.camunda.zeebe.dmn.DecisionEvaluationResult;
import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import io.camunda.zeebe.dmn.impl.VariablesContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCalledDecision;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.engine.state.deployment.PersistedDecisionRequirements;
import io.camunda.zeebe.engine.state.immutable.DecisionState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayInputStream;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

/** Provides decision behavior to the BPMN processors */
public final class BpmnDecisionBehavior {

  private final DecisionEngine decisionEngine;
  private final DecisionState decisionState;
  private final EventTriggerBehavior eventTriggerBehavior;
  private final VariableState variableState;

  public BpmnDecisionBehavior(
      final DecisionEngine decisionEngine,
      final ZeebeState zeebeState,
      final EventTriggerBehavior eventTriggerBehavior) {
    this.decisionEngine = decisionEngine;
    decisionState = zeebeState.getDecisionState();
    variableState = zeebeState.getVariableState();
    this.eventTriggerBehavior = eventTriggerBehavior;
  }

  /**
   * Evaluate a decision during the processing of a bpmn element.
   *
   * @param element the called decision of the current bpmn element
   * @param context process instance-related data of the element that is executed
   * @return either an evaluated decision's result or a failure
   */
  public Either<Failure, DecisionEvaluationResult> evaluateDecision(
      final ExecutableCalledDecision element, final BpmnElementContext context) {
    final var scopeKey = context.getElementInstanceKey();

    final var resultOrFailure =
        findDrgInState(element)
            .flatMap(drg -> evaluateDecisionInDrg(drg, element.getDecisionId(), scopeKey));

    // The output mapping behavior determines what to do with the decision result. Since the output
    // mapping may fail and raise an incident, we need to write the variable to a record. This is
    // because we want to evaluate the decision on element activation, while the output mapping
    // happens on element completion. We don't want to re-evaluate the decision for output mapping
    // related incidents.
    resultOrFailure.ifRight(
        result ->
            triggerProcessEventWithResultVariable(context, element.getResultVariable(), result));

    // the failure must have the correct error type and scope, and we only want to declare this once
    return resultOrFailure.mapLeft(
        failure -> new Failure(failure.getMessage(), ErrorType.CALLED_ELEMENT_ERROR, scopeKey));
  }

  // todo(#8571): avoid parsing drg every time
  private Either<Failure, ParsedDecisionRequirementsGraph> findDrgInState(
      final ExecutableCalledDecision element) {
    return findDecisionById(element.getDecisionId())
        .flatMap(this::findDrgByDecision)
        .mapLeft(
            failure ->
                new Failure(
                    "Expected to evaluate decision id '%s', but %s"
                        .formatted(element.getDecisionId(), failure.getMessage())))
        .flatMap(drg -> parseDrg(drg.getResource()));
  }

  private Either<Failure, PersistedDecision> findDecisionById(final String decisionId) {
    return Either.ofOptional(
            decisionState.findLatestDecisionById(BufferUtil.wrapString(decisionId)))
        .orElse(new Failure("no decision found for id '%s'".formatted(decisionId)));
  }

  private Either<Failure, PersistedDecisionRequirements> findDrgByDecision(
      final PersistedDecision decision) {
    final var key = decision.getDecisionRequirementsKey();
    final var id = decision.getDecisionRequirementsId();
    return Either.ofOptional(decisionState.findDecisionRequirementsByKey(key))
        .orElse(new Failure("no drg found for id '%s'".formatted(bufferAsString(id))));
  }

  private Either<Failure, ParsedDecisionRequirementsGraph> parseDrg(final DirectBuffer resource) {
    return Either.<Failure, DirectBuffer>right(resource)
        .map(BufferUtil::bufferAsArray)
        .map(ByteArrayInputStream::new)
        .map(decisionEngine::parse)
        .flatMap(
            parseResult -> {
              if (parseResult.isValid()) {
                return Either.right(parseResult);
              } else {
                return Either.left(new Failure(parseResult.getFailureMessage()));
              }
            });
  }

  private Either<Failure, DecisionEvaluationResult> evaluateDecisionInDrg(
      final ParsedDecisionRequirementsGraph drg, final String decisionId, final long scopeKey) {
    final var variables = variableState.getVariablesAsDocument(scopeKey);
    final var evaluationContext = new VariablesContext(MsgPackConverter.convertToMap(variables));
    final var result = decisionEngine.evaluateDecisionById(drg, decisionId, evaluationContext);
    if (result.isFailure()) {
      return Either.left(new Failure(result.getFailureMessage()));
    } else {
      return Either.right(result);
    }
  }

  private void triggerProcessEventWithResultVariable(
      final BpmnElementContext context,
      final String resultVariableName,
      final DecisionEvaluationResult result) {
    final DirectBuffer resultVariable =
        serializeToNamedVariable(resultVariableName, result.getOutput());
    eventTriggerBehavior.triggeringProcessEvent(
        context.getProcessDefinitionKey(),
        context.getProcessInstanceKey(),
        context.getElementInstanceKey(),
        context.getElementId(),
        resultVariable);
  }

  private static DirectBuffer serializeToNamedVariable(
      final String name, final DirectBuffer value) {
    final var resultBuffer = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter();
    writer.wrap(resultBuffer, 0);
    writer.writeMapHeader(1);
    writer.writeString(BufferUtil.wrapString(name));
    writer.writeRaw(value);
    return resultBuffer;
  }
}
