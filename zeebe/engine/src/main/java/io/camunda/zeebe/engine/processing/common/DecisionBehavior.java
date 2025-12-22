/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import io.camunda.zeebe.engine.state.deployment.DeployedDrg;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
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
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DecisionBehavior {

  private static final Logger LOGGER = LoggerFactory.getLogger(DecisionBehavior.class);
  private static final DecisionInfo UNKNOWN_DECISION_INFO = new DecisionInfo(-1L, -1);
  private static final String SYNTHESIZED_RULE_ID_PREFIX = "ZB_SYNTH_RULE_ID";
  private static final String SYNTHESIZED_RULE_ID_TEMPLATE = "%s_%s_v%s_r%s";
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

  public Either<Failure, PersistedDecision> findLatestDecisionByIdAndTenant(
      final String decisionId, final String tenantId) {
    return Either.ofOptional(
            decisionState.findLatestDecisionByIdAndTenant(
                BufferUtil.wrapString(decisionId), tenantId))
        .orElse(new Failure("no decision found for id '%s'".formatted(decisionId)))
        .mapLeft(failure -> formatDecisionLookupFailure(failure, decisionId));
  }

  public Either<Failure, PersistedDecision> findDecisionByIdAndDeploymentKeyAndTenant(
      final String decisionId, final long deploymentKey, final String tenantId) {
    return Either.ofOptional(
            decisionState.findDecisionByIdAndDeploymentKey(
                tenantId, BufferUtil.wrapString(decisionId), deploymentKey))
        .orElse(
            new Failure(
                """
                Expected to evaluate decision '%s' with binding type 'deployment', \
                but no such decision found in the deployment with key %s which contained the current process. \
                To resolve this incident, migrate the process instance to a process definition \
                that is deployed together with the intended decision to evaluate.\
                """
                    .formatted(decisionId, deploymentKey)));
  }

  public Either<Failure, PersistedDecision> findDecisionByIdAndVersionTagAndTenant(
      final String decisionId, final String versionTag, final String tenantId) {
    return Either.ofOptional(
            decisionState.findDecisionByIdAndVersionTag(
                tenantId, BufferUtil.wrapString(decisionId), versionTag))
        .orElse(
            new Failure(
                """
                Expected to evaluate decision with id '%s' and version tag '%s', but no such decision found. \
                To resolve this incident, deploy a decision with the given id and version tag.\
                """
                    .formatted(decisionId, versionTag)));
  }

  public Either<Failure, PersistedDecision> findDecisionByIdVersionAndTenant(
      final String decisionId, final int version, final String tenantId) {
    return Either.ofOptional(
            decisionState.findDecisionByIdAndVersion(
                tenantId, BufferUtil.wrapString(decisionId), version))
        .orElse(
            new Failure(
                "Expected to evaluate decision with id '%s' and version '%d', but no such decision found"
                    .formatted(decisionId, version)));
  }

  public Either<Failure, PersistedDecision> findDecisionByKeyAndTenant(
      final long decisionKey, final String tenantId) {
    return Either.ofOptional(decisionState.findDecisionByTenantAndKey(tenantId, decisionKey))
        .orElse(new Failure("no decision found for key '%s'".formatted(decisionKey)))
        .mapLeft(failure -> formatDecisionLookupFailure(failure, decisionKey));
  }

  public Either<Failure, ParsedDecisionRequirementsGraph> findParsedDrgByDecision(
      final PersistedDecision persistedDecision) {
    return findDrgByDecision(persistedDecision)
        .map(DeployedDrg::getParsedDecisionRequirements)
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
      final PersistedDecision decision,
      final DecisionEvaluationResult decisionResult,
      final long decisionEvaluationKey) {

    final var decisionEvaluationEvent =
        new DecisionEvaluationRecord()
            .setDecisionKey(decision.getDecisionKey())
            .setDecisionId(decision.getDecisionId())
            .setDecisionName(decision.getDecisionName())
            .setDecisionVersion(decision.getVersion())
            .setDecisionRequirementsKey(decision.getDecisionRequirementsKey())
            .setDecisionRequirementsId(decision.getDecisionRequirementsId())
            .setTenantId(decision.getTenantId());

    final var decisionKeysByDecisionId =
        decisionState
            .findDecisionsByTenantAndDecisionRequirementsKey(
                decision.getTenantId(), decision.getDecisionRequirementsKey())
            .stream()
            .collect(
                Collectors.toMap(
                    persistedDecision -> bufferAsString(persistedDecision.getDecisionId()),
                    DecisionInfo::new));

    final var generator = new DecisionEvaluationInstanceKeyGenerator(decisionEvaluationKey);
    decisionResult
        .getEvaluatedDecisions()
        .forEach(
            evaluatedDecision ->
                addDecisionToEvaluationEvent(
                    evaluatedDecision,
                    decisionKeysByDecisionId.getOrDefault(
                        evaluatedDecision.decisionId(), UNKNOWN_DECISION_INFO),
                    decisionEvaluationEvent,
                    generator.next()));

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

  private Either<Failure, DeployedDrg> findDrgByDecision(final PersistedDecision decision) {
    final var key = decision.getDecisionRequirementsKey();
    final var id = decision.getDecisionRequirementsId();
    return Either.ofOptional(
            decisionState.findDecisionRequirementsByTenantAndKey(decision.getTenantId(), key))
        .orElse(new Failure("no drg found for id '%s'".formatted(bufferAsString(id))));
  }

  private void addDecisionToEvaluationEvent(
      final EvaluatedDecision evaluatedDecision,
      final DecisionInfo decisionInfo,
      final DecisionEvaluationRecord decisionEvaluationEvent,
      final String decisionEvaluationInstanceKey) {

    final var evaluatedDecisionRecord = decisionEvaluationEvent.evaluatedDecisions().add();
    evaluatedDecisionRecord
        .setDecisionId(evaluatedDecision.decisionId())
        .setDecisionEvaluationInstanceKey(decisionEvaluationInstanceKey)
        .setDecisionName(evaluatedDecision.decisionName())
        .setDecisionKey(decisionInfo.key())
        .setDecisionVersion(decisionInfo.version())
        .setDecisionType(evaluatedDecision.decisionType().name())
        .setDecisionOutput(evaluatedDecision.decisionOutput())
        .setTenantId(decisionEvaluationEvent.getTenantId());

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
    final boolean ruleIdMissing = StringUtils.isBlank(matchedRule.ruleId());
    final var ruleId =
        getOrSynthesizeRuleId(
            evaluatedDecisionRecord.getDecisionId(),
            evaluatedDecisionRecord.getDecisionVersion(),
            matchedRule);
    matchedRuleRecord.setRuleId(ruleId).setRuleIndex(matchedRule.ruleIndex());

    if (ruleIdMissing) {
      LOGGER.warn(
          "DMN evaluation: matched rule without id in decision '{}' (version {}, rule index {}). "
              + "Synthesized surrogate ruleId '{}'. "
              + "To eliminate this warning, add an 'id' attribute to the <rule> and deploy a new DMN version.",
          evaluatedDecisionRecord.getDecisionId(),
          evaluatedDecisionRecord.getDecisionVersion(),
          matchedRule.ruleIndex(),
          ruleId);
    }

    matchedRule
        .evaluatedOutputs()
        .forEach(evaluatedOutput -> addOutputToEvaluationEvent(evaluatedOutput, matchedRuleRecord));
  }

  /**
   * Returns the original ruleId if present; otherwise synthesizes a deterministic surrogate:
   * {prefix}_{decisionId}_v{decisionVersion}_r{ruleIndex}
   *
   * <p>Deterministic per deployed decision version and rule index (no timestamps/randomness).
   * Static prefix makes it obvious this was synthesized by the engine.
   */
  private static String getOrSynthesizeRuleId(
      final String decisionId, final int decisionVersion, final MatchedRule rule) {

    final var ruleId = rule.ruleId();
    if (StringUtils.isNotBlank(ruleId)) {
      return ruleId;
    }

    return SYNTHESIZED_RULE_ID_TEMPLATE.formatted(
        SYNTHESIZED_RULE_ID_PREFIX, decisionId, decisionVersion, rule.ruleIndex());
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
