/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dmn.impl;

import static io.camunda.zeebe.util.buffer.BufferUtil.cloneBuffer;

import io.camunda.zeebe.dmn.DecisionContext;
import io.camunda.zeebe.dmn.DecisionEngine;
import io.camunda.zeebe.dmn.DecisionEvaluationResult;
import io.camunda.zeebe.dmn.EvaluatedDecision;
import io.camunda.zeebe.dmn.ParsedDecisionRequirementsGraph;
import io.camunda.zeebe.feel.impl.FeelToMessagePackTransformer;
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.camunda.dmn.Audit.AuditLog;
import org.camunda.dmn.DmnEngine;
import org.camunda.dmn.DmnEngine.EvalFailure;
import org.camunda.dmn.DmnEngine.EvalResult;
import org.camunda.feel.syntaxtree.Val;
import scala.util.Either;

/**
 * A wrapper around the DMN-Scala decision engine.
 *
 * <p>
 * <li><a href="https://github.com/camunda-community-hub/dmn-scala">GitHub Repository</a>
 * <li><a href="https://github.com/camunda-community-hub/dmn-scala">Documentation</a>
 */
public final class DmnScalaDecisionEngine implements DecisionEngine {

  private static final DirectBuffer NIL_OUTPUT = BufferUtil.wrapArray(MsgPackHelper.NIL);

  private final DmnEngine dmnEngine;
  private final FeelToMessagePackTransformer outputConverter = new FeelToMessagePackTransformer();

  public DmnScalaDecisionEngine() {
    dmnEngine = new DmnEngine.Builder().build();
  }

  @Override
  public ParsedDecisionRequirementsGraph parse(final InputStream dmnResource) {
    if (dmnResource == null) {
      throw new IllegalArgumentException("The input stream must not be null");
    }

    try {
      final var parseResult = dmnEngine.parse(dmnResource);

      if (parseResult.isLeft()) {
        final DmnEngine.Failure failure = parseResult.left().get();
        final var failureMessage = failure.message();

        return new ParseFailureMessage(failureMessage);

      } else {
        final var parsedDmn = parseResult.right().get();

        return ParsedDmnScalaDrg.of(parsedDmn);
      }

    } catch (final Exception e) {
      final var failureMessage = e.getMessage();
      return new ParseFailureMessage(failureMessage);
    }
  }

  @Override
  public DecisionEvaluationResult evaluateDecisionById(
      final ParsedDecisionRequirementsGraph decisionRequirementsGraph,
      final String decisionId,
      final DecisionContext context) {

    Objects.requireNonNull(decisionRequirementsGraph);
    Objects.requireNonNull(decisionId);
    final DecisionContext evalContext = Objects.requireNonNullElse(context, Map::of);

    if (!decisionRequirementsGraph.isValid()) {
      return new EvaluationFailure(
          "Expected to evaluate decision '%s', but the decision requirements graph is invalid"
              .formatted(decisionId),
          decisionId);
    }

    final var parsedDmn = ((ParsedDmnScalaDrg) decisionRequirementsGraph).getParsedDmn();
    // todo(#8092): pass in context that allows fetching variable by name (lazy)
    final Either<EvalFailure, EvalResult> result =
        dmnEngine.eval(parsedDmn, decisionId, evalContext.toMap());
    final AuditLog auditLog =
        result.map(EvalResult::auditLog).getOrElse(() -> result.left().get().auditLog());
    final var evaluatedDecisions =
        Optional.ofNullable(auditLog).map(this::getEvaluatedDecisions).orElse(List.of());

    if (result.isLeft()) {
      final var reason = result.left().get().failure().message();

      // use the target decision's id as the failed decision
      String failedDecisionId = decisionId;
      if (!evaluatedDecisions.isEmpty()) {
        // if we know exactly which decision failed, then we can use that one
        // it's always the last decision that was evaluated
        failedDecisionId = evaluatedDecisions.get(evaluatedDecisions.size() - 1).decisionId();
      }

      return new EvaluationFailure(
          String.format("Expected to evaluate decision '%s', but %s", decisionId, reason),
          failedDecisionId,
          evaluatedDecisions);
    }

    final var evalResult = result.right().get();
    if (evalResult.isNil()) {
      return new EvaluationResult(NIL_OUTPUT, evaluatedDecisions);
    }

    final Object output = evalResult.value();
    if (output instanceof Val val) {
      return new EvaluationResult(toMessagePack(val), evaluatedDecisions);
    }

    throw new IllegalStateException(
        String.format(
            "Expected DMN evaluation result to be of type '%s' but was '%s'",
            Val.class, output.getClass()));
  }

  private List<EvaluatedDecision> getEvaluatedDecisions(final AuditLog auditLog) {
    final var evaluatedDecisions = new ArrayList<EvaluatedDecision>();
    auditLog
        .entries()
        .foreach(
            auditLogEntry -> {
              final var evaluatedDecision =
                  EvaluatedDmnScalaDecision.of(auditLogEntry, this::toMessagePack);
              return evaluatedDecisions.add(evaluatedDecision);
            });

    return evaluatedDecisions;
  }

  private DirectBuffer toMessagePack(final Val value) {
    final var reusedBuffer = outputConverter.toMessagePack(value);
    return cloneBuffer(reusedBuffer);
  }
}
