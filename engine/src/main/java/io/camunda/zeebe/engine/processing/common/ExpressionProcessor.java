/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ResultType;
import io.camunda.zeebe.model.bpmn.util.time.Interval;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ExpressionProcessor {

  private static final EvaluationContext EMPTY_EVALUATION_CONTEXT = x -> null;

  private final DirectBuffer resultView = new UnsafeBuffer();

  private final ExpressionLanguage expressionLanguage;
  private final EvaluationContextLookup evaluationContextLookup;

  public ExpressionProcessor(
      final ExpressionLanguage expressionLanguage, final EvaluationContextLookup lookup) {
    this.expressionLanguage = expressionLanguage;
    evaluationContextLookup = lookup;
  }

  /**
   * Returns a new {@code ExpressionProcessor} instance. This new instance will use {@code
   * primaryContext} for all lookups. Only if it doesn't find a variable in {@code primaryContext},
   * it will lookup variables in the evaluation context of {@code this} evaluation processor
   *
   * @param primaryContext new top level evaluation context
   * @return new instance which uses {@code primaryContext} as new top level evaluation context
   */
  public ExpressionProcessor withPrimaryContext(final EvaluationContext primaryContext) {
    final EvaluationContextLookup combinedLookup =
        scopeKey -> primaryContext.combine(evaluationContextLookup.getContext(scopeKey));
    return new ExpressionProcessor(expressionLanguage, combinedLookup);
  }

  /**
   * Returns a new {@code ExpressionProcessor} instance. This new instance will use {@code
   * secondaryContext} for all lookups which it cannot find in its primary evaluation context
   *
   * @param secondaryContext fallback evaluation context
   * @return new instance which uses {@code secondaryContext} as fallback
   */
  public ExpressionProcessor withSecondaryContext(final EvaluationContext secondaryContext) {
    final EvaluationContextLookup combinedLookup =
        scopeKey -> evaluationContextLookup.getContext(scopeKey).combine(secondaryContext);
    return new ExpressionProcessor(expressionLanguage, combinedLookup);
  }

  /**
   * Evaluates the given expression and returns the result as string. If the evaluation fails or the
   * result is not a string then a failure is returned.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return either the evaluation result as string, or a failure
   */
  public Either<Failure, String> evaluateStringExpression(
      final Expression expression, final long scopeKey) {
    return evaluateExpressionAsEither(expression, scopeKey)
        .flatMap(result -> typeCheck(result, ResultType.STRING, scopeKey))
        .map(EvaluationResult::getString);
  }

  /**
   * Evaluates the given expression and returns the result as string wrapped in {@link
   * DirectBuffer}. If the evaluation fails or the result is not a string then a failure is
   * returned.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return either the evaluation result as buffer, or a failure
   */
  public Either<Failure, DirectBuffer> evaluateStringExpressionAsDirectBuffer(
      final Expression expression, final long scopeKey) {
    return evaluateStringExpression(expression, scopeKey).map(this::wrapResult);
  }

  /**
   * Evaluates the given expression and returns the result as long. If the evaluation fails or the
   * result is not a number then a failure is returned.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return either the evaluation result as long, or a failure
   */
  public Either<Failure, Long> evaluateLongExpression(
      final Expression expression, final long scopeKey) {
    return evaluateExpressionAsEither(expression, scopeKey)
        .flatMap(result -> typeCheck(result, ResultType.NUMBER, scopeKey))
        .map(EvaluationResult::getNumber)
        .map(Number::longValue);
  }

  /**
   * Evaluates the given expression and returns the result as boolean. If the evaluation fails or
   * the result is not a boolean then a failure is returned.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return either the evaluation result as boolean, or a failure
   */
  public Either<Failure, Boolean> evaluateBooleanExpression(
      final Expression expression, final long scopeKey) {
    return evaluateExpressionAsEither(expression, scopeKey)
        .flatMap(result -> typeCheck(result, ResultType.BOOLEAN, scopeKey))
        .map(EvaluationResult::getBoolean);
  }

  /**
   * Evaluates the given expression and returns the result as an Interval. If the evaluation fails
   * or the result is not an interval then a failure is returned.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return either the evaluation result as interval or a failure
   */
  public Either<Failure, Interval> evaluateIntervalExpression(
      final Expression expression, final long scopeKey) {
    final var result = evaluateExpression(expression, scopeKey);
    if (result.isFailure()) {
      return Either.left(
          new Failure(result.getFailureMessage(), ErrorType.EXTRACT_VALUE_ERROR, scopeKey));
    }
    switch (result.getType()) {
      case DURATION:
        return Either.right(new Interval(result.getDuration()));
      case PERIOD:
        return Either.right(new Interval(result.getPeriod()));
      case STRING:
        try {
          return Either.right(Interval.parse(result.getString()));
        } catch (final DateTimeParseException e) {
          return Either.left(
              new Failure(
                  String.format(
                      "Invalid duration format '%s' for expression '%s'",
                      result.getString(), expression.getExpression()),
                  ErrorType.EXTRACT_VALUE_ERROR,
                  scopeKey));
        }
      default:
        final var expected = List.of(ResultType.DURATION, ResultType.PERIOD, ResultType.STRING);
        return Either.left(
            new Failure(
                String.format(
                    "Expected result of the expression '%s' to be one of '%s', but was '%s'",
                    expression.getExpression(), expected, result.getType()),
                ErrorType.EXTRACT_VALUE_ERROR,
                scopeKey));
    }
  }

  /**
   * Evaluates the given expression and returns the result as ZonedDateTime. If the evaluation fails
   * or the result is not a ZonedDateTime then a failure is returned.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return either the evaluation result as ZonedDateTime or a failure
   * @throws EvaluationException if expression evaluation failed
   */
  public Either<Failure, ZonedDateTime> evaluateDateTimeExpression(
      final Expression expression, final Long scopeKey) {
    return evaluateDateTimeExpression(expression, scopeKey, false)
        .flatMap(optionalDateTime -> Either.right(optionalDateTime.get()));
  }

  /**
   * Evaluates the given expression and returns the result as <code>Optional<ZonedDateTime></code>.
   * By using the <code>isNullable</code> flag, it is also possible to control the behavior if a
   * given expression evaluates to an empty String or null.
   *
   * <p>A failure is returned if the expression evaluation fails, or the result is not a valid
   * ZonedDateTime String. If the <code>isNullable</code> flag is set to <code>true</code>, an
   * expression that evaluated to empty String or null is not considered a failure, but returns an
   * <code>Optional.empty()</code> that needs to be handled by the caller of this method.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @param isNullable set to <code>true</code> to ignore empty String or null values instead of
   *     returning a failure
   * @return either the evaluation result as ZonedDateTime or a failure
   * @throws EvaluationException if expression evaluation failed
   */
  public Either<Failure, Optional<ZonedDateTime>> evaluateDateTimeExpression(
      final Expression expression, final Long scopeKey, final boolean isNullable) {
    final var result = evaluateExpression(expression, scopeKey);
    if (result.isFailure()) {
      return Either.left(
          new Failure(result.getFailureMessage(), ErrorType.EXTRACT_VALUE_ERROR, scopeKey));
    }
    if (isNullable && result.getType() == ResultType.NULL) {
      return Either.right(Optional.empty());
    }
    if (result.getType() == ResultType.DATE_TIME) {
      return Either.right(Optional.of(result.getDateTime()));
    }
    if (result.getType() == ResultType.STRING) {
      return evaluateDateTimeExpressionString(result, scopeKey, isNullable);
    }
    final var expected = List.of(ResultType.DATE_TIME, ResultType.STRING);
    return Either.left(
        new Failure(
            String.format(
                "Expected result of the expression '%s' to be one of '%s', but was '%s'",
                expression.getExpression(), expected, result.getType()),
            ErrorType.EXTRACT_VALUE_ERROR,
            scopeKey));
  }

  /**
   * Evaluates the given expression and returns the result no matter the type.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return either the evaluation result as buffer, or a failure if the evaluation fails
   */
  public Either<Failure, DirectBuffer> evaluateAnyExpression(
      final Expression expression, final long scopeKey) {
    final var evaluationResult = evaluateExpressionAsEither(expression, scopeKey);
    return evaluationResult.map(EvaluationResult::toBuffer);
  }

  /**
   * Evaluates the given expression and returns the result as a list. The entries of the list are
   * encoded in MessagePack and can have any type.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return either the evaluation result as a list, or a failure if the evaluation fails
   */
  public Either<Failure, List<DirectBuffer>> evaluateArrayExpression(
      final Expression expression, final long scopeKey) {
    final var evaluationResult = evaluateExpressionAsEither(expression, scopeKey);
    return evaluationResult
        .flatMap(result -> typeCheck(result, ResultType.ARRAY, scopeKey))
        .map(EvaluationResult::getList);
  }

  /**
   * Evaluates the given expression and returns the result as a list of strings.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return either the evaluation result as a list of regular strings, or a failure if the
   *     evaluation fails
   */
  public Either<Failure, List<String>> evaluateArrayOfStringsExpression(
      final Expression expression, final long scopeKey) {
    final var evaluationResult = evaluateExpressionAsEither(expression, scopeKey);
    return evaluationResult
        .flatMap(result -> typeCheck(result, ResultType.ARRAY, scopeKey))
        .map(EvaluationResult::getListOfStrings)
        .flatMap(
            list -> {
              if (list != null) {
                return Either.right(list);
              }
              return Either.left(
                  new Failure(
                      String.format(
                          "Expected result of the expression '%s' to be 'ARRAY' containing 'STRING' items,"
                              + " but was 'ARRAY' containing at least one non-'STRING' item.",
                          expression.getExpression()),
                      ErrorType.EXTRACT_VALUE_ERROR,
                      scopeKey));
            });
  }

  /**
   * Evaluates the given expression and returns the result as String. If the evaluation result is a
   * number it is automatically converted to a string.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return either the evaluation result as String, or a failure if the evaluation fails
   */
  public Either<Failure, String> evaluateMessageCorrelationKeyExpression(
      final Expression expression, final long scopeKey) {
    final var expectedTypes = Set.of(ResultType.STRING, ResultType.NUMBER);
    return evaluateExpressionAsEither(expression, scopeKey)
        .flatMap(result -> typeCheckCorrelationKey(scopeKey, expectedTypes, result, expression))
        .map(this::toStringFromStringOrNumber);
  }

  private Either<Failure, EvaluationResult> typeCheckCorrelationKey(
      final long scopeKey,
      final Set<ResultType> expectedTypes,
      final EvaluationResult result,
      final Expression expression) {
    return typeCheck(result, expectedTypes, scopeKey)
        .mapLeft(
            failure ->
                new Failure(
                    String.format(
                        "Failed to extract the correlation key for '%s': The value must be either a string or a number, but was %s.",
                        expression.getExpression(), result.getType()),
                    ErrorType.EXTRACT_VALUE_ERROR,
                    scopeKey));
  }

  private String toStringFromStringOrNumber(final EvaluationResult result) {
    return result.getType() == ResultType.NUMBER
        ? Long.toString(result.getNumber().longValue())
        : result.getString();
  }

  /**
   * Evaluates the given expression of a variable mapping and returns the result as buffer. If the
   * evaluation fails or the result is not a context then a failure is returned.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return either the evaluation result as buffer, or a failure
   */
  public Either<Failure, DirectBuffer> evaluateVariableMappingExpression(
      final Expression expression, final long scopeKey) {
    return evaluateExpressionAsEither(expression, scopeKey)
        .flatMap(result -> typeCheck(result, ResultType.OBJECT, scopeKey))
        .mapLeft(failure -> new Failure(failure.getMessage(), ErrorType.IO_MAPPING_ERROR, scopeKey))
        .map(EvaluationResult::toBuffer);
  }

  private Either<Failure, EvaluationResult> typeCheck(
      final EvaluationResult result, final ResultType expectedResultType, final long scopeKey) {
    if (result.getType() != expectedResultType) {
      return Either.left(
          new Failure(
              String.format(
                  "Expected result of the expression '%s' to be '%s', but was '%s'.",
                  result.getExpression(), expectedResultType, result.getType()),
              ErrorType.EXTRACT_VALUE_ERROR,
              scopeKey));
    }
    return Either.right(result);
  }

  private Either<Failure, EvaluationResult> typeCheck(
      final EvaluationResult result,
      final Collection<ResultType> expectedResultTypes,
      final long scopeKey) {
    return expectedResultTypes.stream()
        .map(expected -> typeCheck(result, expected, scopeKey))
        .filter(Either::isRight)
        .findFirst()
        .orElse(
            Either.left(
                new Failure(
                    String.format(
                        "Expected result of expression '%s' to be one of '%s', but was '%s'",
                        result.getExpression(), expectedResultTypes, result.getType()),
                    ErrorType.EXTRACT_VALUE_ERROR,
                    scopeKey)));
  }

  private EvaluationResult evaluateExpression(
      final Expression expression, final long variableScopeKey) {

    final EvaluationContext context;
    if (variableScopeKey < 0) {
      context = EMPTY_EVALUATION_CONTEXT;
    } else {
      context = evaluationContextLookup.getContext(variableScopeKey);
    }

    return expressionLanguage.evaluateExpression(expression, context);
  }

  private Either<Failure, EvaluationResult> evaluateExpressionAsEither(
      final Expression expression, final long variableScopeKey) {
    final var result = evaluateExpression(expression, variableScopeKey);
    return result.isFailure()
        ? Either.left(
            new Failure(
                result.getFailureMessage(), ErrorType.EXTRACT_VALUE_ERROR, variableScopeKey))
        : Either.right(result);
  }

  private DirectBuffer wrapResult(final String result) {
    resultView.wrap(result.getBytes());
    return resultView;
  }

  private Either<Failure, Optional<ZonedDateTime>> evaluateDateTimeExpressionString(
      final EvaluationResult result, final Long scopeKey, final boolean isNullable) {
    final var resultString = result.getString();

    if (isNullable && resultString.isEmpty()) {
      return Either.right(Optional.empty());
    }

    try {
      return Either.right(Optional.of(ZonedDateTime.parse(resultString)));
    } catch (final DateTimeParseException e) {
      return Either.left(
          new Failure(
              String.format(
                  "Invalid date-time format '%s' for expression '%s'",
                  resultString, result.getExpression()),
              ErrorType.EXTRACT_VALUE_ERROR,
              scopeKey));
    }
  }

  public static final class EvaluationException extends RuntimeException {
    public EvaluationException(final String message) {
      super(message);
    }
  }

  @FunctionalInterface
  public interface EvaluationContextLookup {
    EvaluationContext getContext(final long scopeKey);
  }
}
