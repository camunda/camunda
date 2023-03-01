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
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.engine.state.deployment.PersistedDecisionRequirements;
import io.camunda.zeebe.engine.state.immutable.DecisionState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.decision.EvaluatedDecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.decision.MatchedRuleRecord;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.collection.Tuple;
import java.io.ByteArrayInputStream;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public class DecisionBehavior {

  private static final DecisionInfo UNKNOWN_DECISION_INFO = new DecisionInfo(-1L, -1);
  private final DecisionEngine decisionEngine;
  private final DecisionState decisionState;
  private final ProcessEngineMetrics metrics;

  public DecisionBehavior(
      final DecisionEngine decisionEngine,
      final ProcessingState processingState,
      final ProcessEngineMetrics metrics) {

    decisionState = processingState.getDecisionState();
    this.decisionEngine = decisionEngine;
    this.metrics = metrics;
  }

  public Either<Failure, PersistedDecision> findDecisionById(final String decisionId) {
    return Either.ofOptional(
            decisionState.findLatestDecisionById(BufferUtil.wrapString(decisionId)))
        .orElse(new Failure("no decision found for id '%s'".formatted(decisionId)))
        .mapLeft(failure -> formatDecisionLookupFailure(failure, decisionId));
  }

  public Either<Failure, PersistedDecision> findDecisionByKey(final long decisionKey) {
    return Either.ofOptional(decisionState.findDecisionByKey(decisionKey))
        .orElse(new Failure("no decision found for key '%s'".formatted(decisionKey)))
        .mapLeft(failure -> formatDecisionLookupFailure(failure, decisionKey));
  }

  public Either<Failure, ParsedDecisionRequirementsGraph> findAndParseDrgByDecision(
      final PersistedDecision persistedDecision) {
    return findDrgByDecision(persistedDecision)
        .flatMap(drg -> parseDrg(drg.getResource()))
        .mapLeft(
            failure ->
                formatDecisionLookupFailure(
                    failure, BufferUtil.bufferAsString(persistedDecision.getDecisionId())));
  }

  public Failure formatDecisionLookupFailure(final Failure failure, final long decisionKey) {
    return formatDecisionLookupFailure(failure, String.valueOf(decisionKey));
  }

  public Failure formatDecisionLookupFailure(final Failure failure, final String decisionId) {
    return new Failure(
        "Expected to evaluate decision '%s', but %s".formatted(decisionId, failure.getMessage()));
  }

  public DecisionEvaluationResult evaluateDecisionInDrg(
      final ParsedDecisionRequirementsGraph drg,
      final String decisionId,
      final DirectBuffer variables) {
    final var evaluationContext = new VariablesContext(MsgPackConverter.convertToMap(variables));
    final var evaluationResult =
        decisionEngine.evaluateDecisionById(drg, decisionId, evaluationContext);

    updateDecisionMetrics(evaluationResult);

    return evaluationResult;
  }

  public Tuple<DecisionEvaluationIntent, DecisionEvaluationRecord> createDecisionEvaluationEvent(
      final PersistedDecision decision, final DecisionEvaluationResult decisionResult) {

    final var decisionEvaluationEvent =
        new DecisionEvaluationRecord()
            .setDecisionKey(decision.getDecisionKey())
            .setDecisionId(decision.getDecisionId())
            .setDecisionName(decision.getDecisionName())
            .setDecisionVersion(decision.getVersion())
            .setDecisionRequirementsKey(decision.getDecisionRequirementsKey())
            .setDecisionRequirementsId(decision.getDecisionRequirementsId());

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

    return new Tuple<>(decisionEvaluationIntent, decisionEvaluationEvent);
  }

  private void updateDecisionMetrics(final DecisionEvaluationResult evaluationResult) {
    if (evaluationResult.isFailure()) {
      metrics.increaseFailedEvaluatedDmnElements(evaluationResult.getEvaluatedDecisions().size());
    } else {
      metrics.increaseSuccessfullyEvaluatedDmnElements(
          evaluationResult.getEvaluatedDecisions().size());
    }
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
