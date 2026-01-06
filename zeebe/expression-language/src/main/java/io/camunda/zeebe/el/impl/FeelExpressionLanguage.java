/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el.impl;

import static io.camunda.zeebe.util.EnsureUtil.ensureNotNull;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.EvaluationWarning;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.feel.impl.FeelFunctionProvider;
import io.camunda.zeebe.feel.impl.FeelToMessagePackTransformer;
import io.camunda.zeebe.feel.impl.MessagePackValueMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.camunda.feel.FeelEngine;
import org.camunda.feel.FeelEngine.Failure;
import org.camunda.feel.FeelEngineClock;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.syntaxtree.ParsedExpression;
import org.camunda.feel.syntaxtree.Val;
import scala.util.Either;

/**
 * A wrapper around the FEEL-Scala expression language.
 *
 * <p>
 * <li><a href="https://github.com/camunda/feel-scala">GitHub Repository</a>
 * <li><a href="https://camunda.github.io/feel-scala">Documentation</a>
 */
public final class FeelExpressionLanguage implements ExpressionLanguage {

  private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\=(.+)", Pattern.DOTALL);

  private final FeelToMessagePackTransformer messagePackTransformer =
      new FeelToMessagePackTransformer();

  private final FeelEngine feelEngine;

  public FeelExpressionLanguage(final FeelEngineClock clock) {
    feelEngine =
        new FeelEngine.Builder()
            .customValueMapper(new MessagePackValueMapper())
            .functionProvider(new FeelFunctionProvider())
            .clock(clock)
            .build();
  }

  @Override
  public Expression parseExpression(final String expression) {
    ensureNotNull("expression", expression);

    final var expressionMatcher = EXPRESSION_PATTERN.matcher(expression);

    if (expressionMatcher.matches()) {
      final var unpackedExpression = expressionMatcher.group(1);
      return parseFeelExpression(unpackedExpression);
    } else {
      return new StaticExpression(expression);
    }
  }

  @Override
  public EvaluationResult evaluateExpression(
      final Expression expression, final EvaluationContext context) {
    ensureNotNull("expression", expression);
    ensureNotNull("context", context);

    if (!expression.isValid()) {
      final var failureMessage = expression.getFailureMessage();
      return new EvaluationFailure(expression, failureMessage);

    } else if (expression instanceof StaticExpression) {
      final var staticExpression = (StaticExpression) expression;
      return staticExpression;

    } else if (expression instanceof FeelExpression) {
      final var feelExpression = (FeelExpression) expression;
      return evaluateFeelExpression(expression, context, feelExpression);
    }

    throw new IllegalArgumentException(
        String.format("Expected FEEL expression or static value but found '%s'", expression));
  }

  private Expression parseFeelExpression(final String expression) {
    final Either<Failure, ParsedExpression> parseResult = feelEngine.parseExpression(expression);

    if (parseResult.isLeft()) {
      final var failure = parseResult.left().get();
      return new InvalidExpression(expression, failure.message());

    } else {
      final var parsedExpression = parseResult.right().get();
      return new FeelExpression(parsedExpression);
    }
  }

  private EvaluationResult evaluateFeelExpression(
      final Expression expression,
      final EvaluationContext context,
      final FeelExpression feelExpression) {

    final var parsedExpression = feelExpression.getParsedExpression();
    final var feelContext = new FeelVariableContext(context);

    final var evaluationResult = feelEngine.evaluate(parsedExpression, feelContext);

    final var evaluationWarnings = extractEvaluationWarning(evaluationResult);
    if (evaluationResult.isFailure()) {
      final var failureMessage = evaluationResult.failure().message();
      return new EvaluationFailure(expression, failureMessage, evaluationWarnings);
    }

    final var result = evaluationResult.result();
    if (result instanceof Val) {
      return new FeelEvaluationResult(
          expression, (Val) result, evaluationWarnings, messagePackTransformer::toMessagePack);

    } else {
      throw new IllegalStateException(
          String.format(
              "Expected FEEL evaluation result to be of type '%s' but was '%s'",
              Val.class, result.getClass()));
    }
  }

  private List<EvaluationWarning> extractEvaluationWarning(
      final org.camunda.feel.api.EvaluationResult evaluationResult) {
    final var warnings = new ArrayList<EvaluationWarning>();
    evaluationResult
        .suppressedFailures()
        .foreach(
            suppressedFailure -> {
              final var warning =
                  new FeelEvaluationWarning(
                      suppressedFailure.failureType().toString(),
                      suppressedFailure.failureMessage());
              return warnings.add(warning);
            });
    return warnings;
  }
}
