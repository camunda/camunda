/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.common;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.dmn.DecisionEngine;
import io.camunda.zeebe.dmn.DecisionEvaluationResult;
import io.camunda.zeebe.dmn.EvaluatedDecision;
import io.camunda.zeebe.dmn.EvaluatedInput;
import io.camunda.zeebe.dmn.EvaluatedOutput;
import io.camunda.zeebe.dmn.MatchedRule;
import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import io.camunda.zeebe.dmn.impl.VariablesContext;
import io.camunda.zeebe.engine.metrics.ProcessEngineMetrics;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.engine.state.deployment.PersistedDecisionRequirements;
import io.camunda.zeebe.engine.state.immutable.DecisionState;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.decision.EvaluatedDecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.decision.MatchedRuleRecord;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.ByteArrayInputStream;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public class DecisionBehavior {

  private static final DecisionInfo UNKNOWN_DECISION_INFO = new DecisionInfo(-1L, -1);
  private final DecisionEngine decisionEngine;
  private final DecisionState decisionState;
  private final VariableState variableState;
  private final ProcessEngineMetrics metrics;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;

  public DecisionBehavior(
      final DecisionEngine decisionEngine,
      final ZeebeState zeebeState,
      final ProcessEngineMetrics metrics,
      final StateWriter stateWriter,
      final KeyGenerator keyGenerator) {
    this.decisionEngine = decisionEngine;
    decisionState = zeebeState.getDecisionState();
    variableState = zeebeState.getVariableState();
    this.metrics = metrics;
    this.stateWriter = stateWriter;
    this.keyGenerator = keyGenerator;
  }

  public Either<Failure, DecisionEvaluationResult> evaluateDecision(
      final String decisionId, final long scopeKey, final BpmnElementContext bpmnContext) {

    // todo(#8571): avoid parsing drg every time
    final var decisionOrFailure = findDecisionById(decisionId);
    final var resultOrFailure =
        decisionOrFailure
            .flatMap(this::findDrgByDecision)
            .mapLeft(
                failure ->
                    new Failure(
                        "Expected to evaluate decision '%s', but %s"
                            .formatted(decisionId, failure.getMessage())))
            .flatMap(drg -> parseDrg(drg.getResource()))
            // all the above failures have the same error type and the correct scope
            .mapLeft(f -> new Failure(f.getMessage(), ErrorType.CALLED_DECISION_ERROR, scopeKey))
            .flatMap(
                drg -> {
                  final var evaluationResult = evaluateDecisionInDrg(drg, decisionId, scopeKey);

                  final var decision = decisionOrFailure.get();
                  writeDecisionEvaluationEvent(decision, evaluationResult, bpmnContext);

                  if (evaluationResult.isFailure()) {
                    metrics.increaseFailedEvaluatedDmnElements(
                        evaluationResult.getEvaluatedDecisions().size());
                    return Either.left(
                        new Failure(
                            evaluationResult.getFailureMessage(),
                            ErrorType.DECISION_EVALUATION_ERROR,
                            scopeKey));
                  } else {
                    metrics.increaseSuccessfullyEvaluatedDmnElements(
                        evaluationResult.getEvaluatedDecisions().size());
                    return Either.right(evaluationResult);
                  }
                });

    return resultOrFailure;
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

  private DecisionEvaluationResult evaluateDecisionInDrg(
      final ParsedDecisionRequirementsGraph drg, final String decisionId, final long scopeKey) {
    final var variables = variableState.getVariablesAsDocument(scopeKey);
    final var evaluationContext = new VariablesContext(MsgPackConverter.convertToMap(variables));
    return decisionEngine.evaluateDecisionById(drg, decisionId, evaluationContext);
  }

  private void writeDecisionEvaluationEvent(
      final PersistedDecision decision,
      final DecisionEvaluationResult decisionResult,
      final BpmnElementContext bpmnContext) {

    final var decisionEvaluationEvent =
        new DecisionEvaluationRecord()
            .setDecisionKey(decision.getDecisionKey())
            .setDecisionId(decision.getDecisionId())
            .setDecisionName(decision.getDecisionName())
            .setDecisionVersion(decision.getVersion())
            .setDecisionRequirementsKey(decision.getDecisionRequirementsKey())
            .setDecisionRequirementsId(decision.getDecisionRequirementsId());

    if (bpmnContext != null) {
      decisionEvaluationEvent
          .setProcessDefinitionKey(bpmnContext.getProcessDefinitionKey())
          .setBpmnProcessId(bpmnContext.getBpmnProcessId())
          .setProcessInstanceKey(bpmnContext.getProcessInstanceKey())
          .setElementInstanceKey(bpmnContext.getElementInstanceKey())
          .setElementId(bpmnContext.getElementId());
    }

    final var decisionKeysByDecisionId =
        decisionState
            .findDecisionsByDecisionRequirementsKey(decision.getDecisionRequirementsKey())
            .stream()
            .collect(
                Collectors.toMap(
                    persistedDecision -> bufferAsString(persistedDecision.getDecisionId()),
                    DecisionInfo::new));

    decisionResult
        .getEvaluatedDecisions()
        .forEach(
            evaluatedDecision ->
                addDecisionToEvaluationEvent(
                    evaluatedDecision,
                    decisionKeysByDecisionId.getOrDefault(
                        evaluatedDecision.decisionId(), UNKNOWN_DECISION_INFO),
                    decisionEvaluationEvent));

    final DecisionEvaluationIntent decisionEvaluationIntent;
    if (decisionResult.isFailure()) {
      decisionEvaluationIntent = DecisionEvaluationIntent.FAILED;

      decisionEvaluationEvent
          .setEvaluationFailureMessage(decisionResult.getFailureMessage())
          .setFailedDecisionId(decisionResult.getFailedDecisionId());
    } else {
      decisionEvaluationIntent = DecisionEvaluationIntent.EVALUATED;

      decisionEvaluationEvent.setDecisionOutput(decisionResult.getOutput());
    }

    final var newDecisionEvaluationKey = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(
        newDecisionEvaluationKey, decisionEvaluationIntent, decisionEvaluationEvent);
  }

  private void addDecisionToEvaluationEvent(
      final EvaluatedDecision evaluatedDecision,
      final DecisionInfo decisionInfo,
      final DecisionEvaluationRecord decisionEvaluationEvent) {

    final var evaluatedDecisionRecord = decisionEvaluationEvent.evaluatedDecisions().add();
    evaluatedDecisionRecord
        .setDecisionId(evaluatedDecision.decisionId())
        .setDecisionName(evaluatedDecision.decisionName())
        .setDecisionKey(decisionInfo.key())
        .setDecisionVersion(decisionInfo.version())
        .setDecisionType(evaluatedDecision.decisionType().name())
        .setDecisionOutput(evaluatedDecision.decisionOutput());

    evaluatedDecision
        .evaluatedInputs()
        .forEach(
            evaluatedInput -> addInputToEvaluationEvent(evaluatedInput, evaluatedDecisionRecord));

    evaluatedDecision
        .matchedRules()
        .forEach(
            matchedRule -> addMatchedRuleToEvaluationEvent(matchedRule, evaluatedDecisionRecord));
  }

  private void addMatchedRuleToEvaluationEvent(
      final MatchedRule matchedRule, final EvaluatedDecisionRecord evaluatedDecisionRecord) {

    final var matchedRuleRecord = evaluatedDecisionRecord.matchedRules().add();
    matchedRuleRecord.setRuleId(matchedRule.ruleId()).setRuleIndex(matchedRule.ruleIndex());

    matchedRule
        .evaluatedOutputs()
        .forEach(evaluatedOutput -> addOutputToEvaluationEvent(evaluatedOutput, matchedRuleRecord));
  }

  private void addInputToEvaluationEvent(
      final EvaluatedInput evaluatedInput, final EvaluatedDecisionRecord evaluatedDecisionRecord) {
    final var inputRecord =
        evaluatedDecisionRecord
            .evaluatedInputs()
            .add()
            .setInputId(evaluatedInput.inputId())
            .setInputValue(evaluatedInput.inputValue());

    if (evaluatedInput.inputName() != null) {
      inputRecord.setInputName(evaluatedInput.inputName());
    }
  }

  private void addOutputToEvaluationEvent(
      final EvaluatedOutput evaluatedOutput, final MatchedRuleRecord matchedRuleRecord) {
    final var outputRecord =
        matchedRuleRecord
            .evaluatedOutputs()
            .add()
            .setOutputId(evaluatedOutput.outputId())
            .setOutputValue(evaluatedOutput.outputValue());

    if (evaluatedOutput.outputName() != null) {
      outputRecord.setOutputName(evaluatedOutput.outputName());
    }
  }

  private record DecisionInfo(long key, int version) {
    DecisionInfo(final PersistedDecision persistedDecision) {
      this(persistedDecision.getDecisionKey(), persistedDecision.getVersion());
    }
  }
}
