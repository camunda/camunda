/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dmn.impl;

import static java.util.Map.entry;

import io.camunda.zeebe.dmn.DecisionType;
import io.camunda.zeebe.dmn.EvaluatedDecision;
import io.camunda.zeebe.dmn.EvaluatedInput;
import io.camunda.zeebe.dmn.MatchedRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.camunda.dmn.Audit.AuditLogEntry;
import org.camunda.dmn.Audit.DecisionTableEvaluationResult;
import org.camunda.dmn.parser.ParsedContext;
import org.camunda.dmn.parser.ParsedDecisionLogic;
import org.camunda.dmn.parser.ParsedDecisionTable;
import org.camunda.dmn.parser.ParsedInvocation;
import org.camunda.dmn.parser.ParsedList;
import org.camunda.dmn.parser.ParsedLiteralExpression;
import org.camunda.dmn.parser.ParsedRelation;
import org.camunda.dmn.parser.ParsedRule;
import org.camunda.feel.syntaxtree.Val;

public record EvaluatedDmnScalaDecision(
    String decisionId,
    String decisionName,
    DecisionType decisionType,
    DirectBuffer decisionOutput,
    List<EvaluatedInput> evaluatedInputs,
    List<MatchedRule> matchedRules)
    implements EvaluatedDecision {

  private static final Map<Class<? extends ParsedDecisionLogic>, DecisionType>
      DECISION_TYPE_MAPPING =
          Map.ofEntries(
              entry(ParsedDecisionTable.class, DecisionType.DECISION_TABLE),
              entry(ParsedLiteralExpression.class, DecisionType.LITERAL_EXPRESSION),
              entry(ParsedContext.class, DecisionType.CONTEXT),
              entry(ParsedList.class, DecisionType.LIST),
              entry(ParsedRelation.class, DecisionType.RELATION),
              entry(ParsedInvocation.class, DecisionType.INVOCATION));

  public static EvaluatedDmnScalaDecision of(
      final AuditLogEntry auditLogEntry, final Function<Val, DirectBuffer> converter) {
    final DecisionType decisionType = getDecisionType(auditLogEntry.decisionLogic());
    final var evaluationResult = auditLogEntry.result();
    final var decisionOutput = converter.apply(evaluationResult.result());

    final var evaluatedInputs = new ArrayList<EvaluatedInput>();
    final var matchedRules = new ArrayList<MatchedRule>();

    if (evaluationResult instanceof DecisionTableEvaluationResult decisionTableResult) {
      decisionTableResult
          .inputs()
          .foreach(
              input -> {
                final var evaluatedInput = EvaluatedDmnScalaInput.of(input, converter);
                return evaluatedInputs.add(evaluatedInput);
              });

      decisionTableResult
          .matchedRules()
          .foreach(
              evaluatedRule -> {
                final var matchedRule =
                    MatchedDmnScalaRule.of(
                        evaluatedRule,
                        getRuleIndex(auditLogEntry.decisionLogic(), evaluatedRule.rule()),
                        converter);
                return matchedRules.add(matchedRule);
              });
    }

    return new EvaluatedDmnScalaDecision(
        auditLogEntry.id(),
        auditLogEntry.name(),
        decisionType,
        decisionOutput,
        evaluatedInputs,
        matchedRules);
  }

  private static DecisionType getDecisionType(final ParsedDecisionLogic decisionLogic) {
    return DECISION_TYPE_MAPPING.getOrDefault(decisionLogic.getClass(), DecisionType.UNKNOWN);
  }

  // TODO (dmn-scala#136): read the rule index from the parsed rule object
  private static int getRuleIndex(final ParsedDecisionLogic decisionLogic, final ParsedRule rule) {

    if (decisionLogic instanceof ParsedDecisionTable decisionTable) {
      final var rules = decisionTable.rules().toList();
      return rules.indexOf(rule) + 1;

    } else {
      return -1;
    }
  }
}
