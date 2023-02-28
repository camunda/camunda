/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.dmn.DecisionEvaluationResult;
import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.DecisionBehavior;
import io.camunda.zeebe.engine.processing.common.EventTriggerBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCalledDecision;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.collection.Tuple;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;

/** Provides decision behavior to the BPMN processors */
public final class BpmnDecisionBehavior {

  private final DecisionBehavior decisionBehavior;
  private final EventTriggerBehavior eventTriggerBehavior;
  private final VariableState variableState;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final ExpressionProcessor expressionBehavior;

  public BpmnDecisionBehavior(
      final DecisionBehavior decisionBehavior,
      final ProcessingState processingState,
      final EventTriggerBehavior eventTriggerBehavior,
      final StateWriter stateWriter,
      final KeyGenerator keyGenerator,
      final ExpressionProcessor expressionBehavior) {

    variableState = processingState.getVariableState();
    this.decisionBehavior = decisionBehavior;
    this.eventTriggerBehavior = eventTriggerBehavior;
    this.stateWriter = stateWriter;
    this.keyGenerator = keyGenerator;
    this.expressionBehavior = expressionBehavior;
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

    final var decisionIdOrFailure = evalDecisionIdExpression(element, scopeKey);
    if (decisionIdOrFailure.isLeft()) {
      return Either.left(decisionIdOrFailure.getLeft());
    }

    final var decisionId = decisionIdOrFailure.get();
    final var decisionOrFailure = decisionBehavior.findDecisionById(decisionId);
    final Either<Failure, ParsedDecisionRequirementsGraph> drgOrFailure =
        decisionOrFailure
            .flatMap(decision -> decisionBehavior.findAndParseDrgByDecision(decision))
            // any failures above have the same error type and the correct scope
            // decisions invoked by business rule tasks have a different error type
            .mapLeft(
                failure ->
                    new Failure(failure.getMessage(), ErrorType.CALLED_DECISION_ERROR, scopeKey));

    final var variables = variableState.getVariablesAsDocument(scopeKey);
    final var resultOrFailure =
        drgOrFailure.flatMap(
            drg -> {
              final var decision = decisionOrFailure.get();
              final var evaluationResult =
                  decisionBehavior.evaluateDecisionInDrg(drg, decisionId, variables);

              final Tuple<DecisionEvaluationIntent, DecisionEvaluationRecord> eventTuple =
                  decisionBehavior.createDecisionEvaluationEvent(decision, evaluationResult);
              writeDecisionEvaluationEvent(eventTuple, context);

              if (evaluationResult.isFailure()) {
                return Either.left(
                    new Failure(
                        evaluationResult.getFailureMessage(),
                        ErrorType.DECISION_EVALUATION_ERROR,
                        scopeKey));
              } else {
                return Either.right(evaluationResult);
              }
            });

    resultOrFailure.ifRight(
        result -> {
          // The output mapping behavior determines what to do with the decision result. Since the
          // output mapping may fail and raise an incident, we need to write the variable to a
          // record. This is because we want to evaluate the decision on element activation, while
          // the output mapping happens on element completion. We don't want to re-evaluate the
          // decision for output mapping related incidents.
          triggerProcessEventWithResultVariable(context, element.getResultVariable(), result);
        });

    return resultOrFailure;
  }

  private Either<Failure, String> evalDecisionIdExpression(
      final ExecutableCalledDecision element, final long scopeKey) {
    return expressionBehavior.evaluateStringExpression(element.getDecisionId(), scopeKey);
  }

  private void writeDecisionEvaluationEvent(
      final Tuple<DecisionEvaluationIntent, DecisionEvaluationRecord> decisionEvaluationEventTuple,
      final BpmnElementContext context) {

    final DecisionEvaluationRecord evaluationEvent = decisionEvaluationEventTuple.getRight();

    evaluationEvent
        .setProcessDefinitionKey(context.getProcessDefinitionKey())
        .setBpmnProcessId(context.getBpmnProcessId())
        .setProcessInstanceKey(context.getProcessInstanceKey())
        .setElementInstanceKey(context.getElementInstanceKey())
        .setElementId(context.getElementId());

    final var newDecisionEvaluationKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(
        newDecisionEvaluationKey,
        decisionEvaluationEventTuple.getLeft(),
        decisionEvaluationEventTuple.getRight());
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
