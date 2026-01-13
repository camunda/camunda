/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ExpressionProcessor {

  private static final List<ResultType> INTERVAL_RESULT_TYPES =
      List.of(ResultType.DURATION, ResultType.PERIOD, ResultType.STRING);
  private static final List<ResultType> DATE_TIME_RESULT_TYPES =
      List.of(ResultType.DATE_TIME, ResultType.STRING);
  private static final List<ResultType> NULLABLE_DATE_TIME_RESULT_TYPES =
      List.of(ResultType.NULL, ResultType.DATE_TIME, ResultType.STRING);

  private static final EvaluationContext EMPTY_EVALUATION_CONTEXT = x -> null;
  private static final Duration EXPRESSION_EVALUATION_TIMEOUT = Duration.ofSeconds(1);

  private final DirectBuffer resultView = new UnsafeBuffer();

  private final ExpressionLanguage expressionLanguage;
  private final EvaluationContextLookup evaluationContextLookup;
  private final Duration expressionEvaluationTimeout;

  public ExpressionProcessor(
      final ExpressionLanguage expressionLanguage, final EvaluationContextLookup lookup) {
    this(expressionLanguage, lookup, Duration.ofSeconds(1));
  }

  public ExpressionProcessor(
      final ExpressionLanguage expressionLanguage,
      final EvaluationContextLookup lookup,
      final Duration expressionEvaluationTimeout) {
    this.expressionLanguage = expressionLanguage;
    evaluationContextLookup = lookup;
    this.expressionEvaluationTimeout = expressionEvaluationTimeout;
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
    return new ExpressionProcessor(expressionLanguage, combinedLookup, expressionEvaluationTimeout);
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
    return new ExpressionProcessor(expressionLanguage, combinedLookup, expressionEvaluationTimeout);
  }

  /**
   * Evaluates the given expression and returns the result as string. If the evaluation fails or the
   * result is not a string then a failure is returned.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return either the evaluation result as string, or a failure
   * @throws EvaluationException if the evaluation is interrupted or fails unexpectedly
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
   * @throws EvaluationException if the evaluation is interrupted or fails unexpectedly
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
   * @throws EvaluationException if the evaluation is interrupted or fails unexpectedly
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
   * @throws EvaluationException if the evaluation is interrupted or fails unexpectedly
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
   * @throws EvaluationException if the evaluation is interrupted or fails unexpectedly
   */
  public Either<Failure, Interval> evaluateIntervalExpression(
      final Expression expression, final long scopeKey) {
    return evaluateExpressionAsEither(expression, scopeKey)
        .flatMap(result -> typeCheck(result, INTERVAL_RESULT_TYPES, scopeKey))
        .flatMap(
            result ->
                switch (result.getType()) {
                  case DURATION -> Either.right(new Interval(result.getDuration()));
                  case PERIOD -> Either.right(new Interval(result.getPeriod()));
                  default -> parseIntervalString(expression, scopeKey, result);
                });
  }

  private Either<Failure, Interval> parseIntervalString(
      final Expression expression, final long scopeKey, final EvaluationResult result) {
    try {
      return Either.right(Interval.parse(result.getString()));
    } catch (final DateTimeParseException e) {
      return Either.left(
          createFailureMessage(
              result,
              String.format(
                  "Invalid duration format '%s' for expression '%s'.",
                  result.getString(), expression.getExpression()),
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
   * @throws EvaluationException if the evaluation is interrupted or fails unexpectedly
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
    final var dateTimeResultTypes =
        isNullable ? NULLABLE_DATE_TIME_RESULT_TYPES : DATE_TIME_RESULT_TYPES;
    return evaluateExpressionAsEither(expression, scopeKey)
        .flatMap(result -> typeCheck(result, dateTimeResultTypes, scopeKey))
        .flatMap(
            result ->
                switch (result.getType()) {
                  case NULL -> Either.right(Optional.empty());
                  case DATE_TIME -> Either.right(Optional.of(result.getDateTime()));
                  default -> evaluateDateTimeExpressionString(result, scopeKey, isNullable);
                });
  }

  /**
   * Evaluates the given expression and returns the result no matter the type.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return either the evaluation result as buffer, or a failure if the evaluation fails
   * @throws EvaluationException if the evaluation is interrupted or fails unexpectedly
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
   * @throws EvaluationException if the evaluation is interrupted or fails unexpectedly
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
   * @throws EvaluationException if the evaluation is interrupted or fails unexpectedly
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
                  createFailureMessage(
                      evaluationResult.get(),
                      String.format(
                          "Expected result of the expression '%s' to be 'ARRAY' containing 'STRING' items,"
                              + " but was 'ARRAY' containing at least one non-'STRING' item.",
                          expression.getExpression()),
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
   * @throws EvaluationException if the evaluation is interrupted or fails unexpectedly
   */
  public Either<Failure, String> evaluateMessageCorrelationKeyExpression(
      final Expression expression, final long scopeKey) {
    final var expectedTypes = Set.of(ResultType.STRING, ResultType.NUMBER);
    return evaluateExpressionAsEither(expression, scopeKey)
        .flatMap(result -> typeCheckCorrelationKey(scopeKey, expectedTypes, result, expression))
        .map(this::toStringFromStringOrNumber);
  }

  /**
   * Evaluates the given expression and returns the result as int. If the evaluation fails or the
   * result is not a number then a failure is returned.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return either the evaluation result as int, or a failure
   * @throws EvaluationException if the evaluation is interrupted or fails unexpectedly
   */
  public Either<Failure, Integer> evaluateIntegerExpression(
      final Expression expression, final long scopeKey) {
    return evaluateExpressionAsEither(expression, scopeKey)
        .flatMap(result -> typeCheck(result, ResultType.NUMBER, scopeKey))
        .flatMap(
            result -> {
              if (result.getNumber().doubleValue() % 1 == 0) {
                return Either.right(result.getNumber().intValue());
              } else {
                return Either.left(
                    createFailureMessage(
                        result,
                        String.format(
                            "Expected result of the expression '%s' to be an integer, but was a decimal.",
                            expression.getExpression()),
                        scopeKey));
              }
            });
  }

  private Either<Failure, EvaluationResult> typeCheckCorrelationKey(
      final long scopeKey,
      final Set<ResultType> expectedTypes,
      final EvaluationResult result,
      final Expression expression) {
    return typeCheck(result, expectedTypes, scopeKey)
        .mapLeft(
            failure ->
                createFailureMessage(
                    result,
                    String.format(
                        "Failed to extract the correlation key for '%s': The value must be either a string or a number, but was '%s'.",
                        expression.getExpression(), result.getType()),
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
   * @throws EvaluationException if the evaluation is interrupted or fails unexpectedly
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
          createFailureMessage(
              result,
              String.format(
                  "Expected result of the expression '%s' to be '%s', but was '%s'.",
                  result.getExpression(), expectedResultType, result.getType()),
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
                createFailureMessage(
                    result,
                    String.format(
                        "Expected result of the expression '%s' to be one of '%s', but was '%s'.",
                        result.getExpression(), expectedResultTypes, result.getType()),
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
    final CompletableFuture<EvaluationResult> future =
        CompletableFuture.supplyAsync(() -> evaluateExpression(expression, variableScopeKey));

    final EvaluationResult result;
    try {
      result = future.get(expressionEvaluationTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final TimeoutException e) {
      future.cancel(true);
      return Either.left(
          new Failure(
              "Expected to evaluate expression but timed out after %s ms: '%s'"
                  .formatted(expressionEvaluationTimeout.toMillis(), expression.getExpression()),
              ErrorType.EXTRACT_VALUE_ERROR,
              variableScopeKey));

    } catch (final InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      final var message =
          "Expected to evaluate expression '%s', but the evaluation was interrupted"
              .formatted(expression.getExpression());
      throw new EvaluationException(message, e);

    } catch (final ExecutionException e) {
      final var message =
          "Expected to evaluate expression '%s', but an exception was thrown"
              .formatted(expression.getExpression());
      throw new EvaluationException(message, e.getCause());
    }

    return result.isFailure()
        ? Either.left(createFailureMessage(result, result.getFailureMessage(), variableScopeKey))
        : Either.right(result);
  }

  private Failure createFailureMessage(
      final EvaluationResult evaluationResult,
      final String failureMessage,
      final long variableScopeKey) {
    var message = failureMessage;
    final var evaluationWarnings = evaluationResult.getWarnings();
    if (!evaluationWarnings.isEmpty()) {
      final var formattedWarnings =
          evaluationWarnings.stream()
              .map(warning -> "[%s] %s".formatted(warning.getType(), warning.getMessage()))
              .collect(Collectors.joining("\n"));
      message +=
          " The evaluation reported the following warnings:\n%s".formatted(formattedWarnings);
    }

    return new Failure(message, ErrorType.EXTRACT_VALUE_ERROR, variableScopeKey);
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
          createFailureMessage(
              result,
              String.format(
                  "Invalid date-time format '%s' for expression '%s'.",
                  resultString, result.getExpression()),
              scopeKey));
    }
  }

  public static final class EvaluationException extends RuntimeException {
    public EvaluationException(final String message) {
      super(message);
    }

    public EvaluationException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }

  @FunctionalInterface
  public interface EvaluationContextLookup {
    EvaluationContext getContext(final long scopeKey);
  }
}
