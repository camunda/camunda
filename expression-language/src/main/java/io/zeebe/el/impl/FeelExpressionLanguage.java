/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.el.impl;

import static io.zeebe.util.EnsureUtil.ensureNotNull;

import io.zeebe.el.EvaluationResult;
import io.zeebe.el.Expression;
import io.zeebe.el.ExpressionLanguage;
import java.util.regex.Pattern;
import org.agrona.DirectBuffer;
import org.camunda.feel.FeelEngine;
import org.camunda.feel.FeelEngine.Failure;
import org.camunda.feel.ParsedExpression;
import org.camunda.feel.spi.JavaValueMapper;
import scala.util.Either;

/**
 * A wrapper around the FEEL-Scala expression language.
 *
 * <p>
 * <li><a href="https://github.com/camunda/feel-scala">GitHub Repository</a>
 * <li><a href="https://camunda.github.io/feel-scala">Documentation</a>
 */
public final class FeelExpressionLanguage implements ExpressionLanguage {

  private static final Pattern EXPRESSION_PATTERN = Pattern.compile("\\=(.+)");
  private static final Pattern STATIC_VALUE_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_\\-]*");

  private final FeelEngine feelEngine =
      new FeelEngine.Builder().customValueMapper(new JavaValueMapper()).build();

  private final MessagePackConverter messagePackConverter = new MessagePackConverter();

  @Override
  public Expression parseExpression(final String expression) {
    ensureNotNull("expression", expression);

    final var expressionMatcher = EXPRESSION_PATTERN.matcher(expression);
    final var valueMather = STATIC_VALUE_PATTERN.matcher(expression);

    if (expressionMatcher.matches()) {
      final var unpackedExpression = expressionMatcher.group(1);
      return parseFeelExpression(unpackedExpression);

    } else if (valueMather.matches()) {
      final var value = valueMather.group();
      return new StaticExpression(value);

    } else {
      final var failureMessage =
          String.format(
              "Expected FEEL expression (e.g. '=variableName') or static value (e.g. 'jobType') but found '%s'",
              expression);
      return new InvalidExpression(expression, failureMessage);
    }
  }

  @Override
  public EvaluationResult evaluateExpression(
      final Expression expression, final DirectBuffer variables) {
    ensureNotNull("expression", expression);
    ensureNotNull("variables", variables);

    if (!expression.isValid()) {
      final var failureMessage = expression.getFailureMessage();
      return new EvaluationFailure(expression, failureMessage);

    } else if (expression instanceof StaticExpression) {
      final var staticExpression = (StaticExpression) expression;
      return staticExpression;

    } else if (expression instanceof FeelExpression) {
      final var feelExpression = (FeelExpression) expression;
      return evaluateFeelExpression(expression, variables, feelExpression);
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
      final DirectBuffer variables,
      final FeelExpression feelExpression) {

    final var parsedExpression = feelExpression.getParsedExpression();
    final var variablesAsMap = messagePackConverter.readMessagePack(variables);

    final Either<Failure, Object> evalResult = feelEngine.eval(parsedExpression, variablesAsMap);

    if (evalResult.isLeft()) {
      final var failure = evalResult.left().get();
      return new EvaluationFailure(expression, failure.message());

    } else {
      final var result = evalResult.right().get();
      return new FeelEvaluationResult(expression, result);
    }
  }
}
