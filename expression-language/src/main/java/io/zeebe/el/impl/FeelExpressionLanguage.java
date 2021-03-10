/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.el.impl;

import static io.zeebe.util.EnsureUtil.ensureNotNull;

import io.zeebe.el.EvaluationContext;
import io.zeebe.el.EvaluationResult;
import io.zeebe.el.Expression;
import io.zeebe.el.ExpressionLanguage;
import io.zeebe.el.impl.feel.FeelEvaluationResult;
import io.zeebe.el.impl.feel.FeelFunctionProvider;
import io.zeebe.el.impl.feel.FeelToMessagePackTransformer;
import io.zeebe.el.impl.feel.FeelVariableContext;
import io.zeebe.el.impl.feel.MessagePackValueMapper;
import io.zeebe.util.sched.clock.ActorClock;
import java.util.regex.Pattern;
import org.camunda.feel.FeelEngine;
import org.camunda.feel.FeelEngine.Failure;
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

  public FeelExpressionLanguage(final ActorClock clock) {
    feelEngine =
        new FeelEngine.Builder()
            .customValueMapper(new MessagePackValueMapper())
            .functionProvider(new FeelFunctionProvider())
            .clock(new ZeebeFeelEngineClock(clock))
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

    final Either<Failure, Object> evalResult = feelEngine.eval(parsedExpression, feelContext);

    if (evalResult.isLeft()) {
      final var failure = evalResult.left().get();
      return new EvaluationFailure(expression, failure.message());
    }

    final var result = evalResult.right().get();

    if (result instanceof Val) {
      return new FeelEvaluationResult(
          expression, (Val) result, messagePackTransformer::toMessagePack);

    } else {
      throw new IllegalStateException(
          String.format(
              "Expected FEEL evaluation result to be of type '%s' but was '%s'",
              Val.class, result.getClass()));
    }
  }
}
