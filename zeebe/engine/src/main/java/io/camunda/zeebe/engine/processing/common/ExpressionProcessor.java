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
import io.camunda.zeebe.engine.processing.expression.CombinedEvaluationContext;
import io.camunda.zeebe.engine.processing.expression.ScopedEvaluationContext;
import io.camunda.zeebe.model.bpmn.util.time.Interval;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

  private final DirectBuffer resultView = new UnsafeBuffer();

  private final ExpressionLanguage expressionLanguage;
  private final ScopedEvaluationContext scopedEvaluationContext;

  public ExpressionProcessor(
      final ExpressionLanguage expressionLanguage,
      final ScopedEvaluationContext scopedEvaluationContext) {
    this.expressionLanguage = expressionLanguage;
    this.scopedEvaluationContext = scopedEvaluationContext;
  }

  /**
   * Creates a new {@code ExpressionProcessor} with an additional evaluation context placed in front
   * of the current one.
   *
   * <p>The provided {@code scopedEvaluationContext} becomes the highest-priority lookup context.
   * When resolving expressions, variable lookup proceeds in this order:
   *
   * <ol>
   *   <li>the newly provided {@code scopedEvaluationContext} (top-level)
   *   <li>the existing context associated with this processor
   * </ol>
   *
   * <p>No mutation is done on the current processor — a new instance is returned.
   *
   * @param scopedEvaluationContext the evaluation context to prepend and give priority to
   * @return a new {@code ExpressionProcessor} whose lookup order starts with the given context
   */
  public ExpressionProcessor prependContext(final ScopedEvaluationContext scopedEvaluationContext) {
    return new ExpressionProcessor(
        expressionLanguage,
        CombinedEvaluationContext.withContexts(
            scopedEvaluationContext, this.scopedEvaluationContext));
  }

  /**
   * Evaluates the given expression and attempts to return the result as a {@link String}.
   *
   * <p>Expression resolution follows the provided {@code scopeKey} and {@code tenantId} when
   * looking up variables:
   *
   * <ul>
   *   <li>If {@code scopeKey} is negative, the evaluation is performed with an empty variable
   *       context.
   *   <li>Otherwise, variables are resolved using the context derived from the given scope and
   *       tenant.
   * </ul>
   *
   * <p>If the expression cannot be evaluated, or if the evaluation result is not a string, a {@code
   * Failure} is returned instead of throwing an exception.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope from which variables should be loaded; negative implies no context
   * @param tenantId the tenant owning the scope, used to resolve variables in multi-tenant setups
   * @return an {@code Either} containing either the evaluated string result, or a {@code Failure}
   */
  public Either<Failure, String> evaluateStringExpression(
      final Expression expression, final long scopeKey, final String tenantId) {
    return evaluateExpressionAsEither(expression, scopeKey, tenantId)
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
      final Expression expression, final long scopeKey, final String tenantId) {
    return evaluateStringExpression(expression, scopeKey, tenantId).map(this::wrapResult);
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
      final Expression expression, final long scopeKey, final String tenantId) {
    return evaluateExpressionAsEither(expression, scopeKey, tenantId)
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
      final Expression expression, final long scopeKey, final String tenantId) {
    return evaluateExpressionAsEither(expression, scopeKey, tenantId)
        .flatMap(result -> typeCheck(result, ResultType.BOOLEAN, scopeKey))
        .map(EvaluationResult::getBoolean);
  }

  public Either<Failure, Boolean> evaluateBooleanExpression(
      final Expression expression, final EvaluationContext context) {
    return evaluateExpressionAsEither(expression, context)
        // scopeKey is -1 because the context is already provided
        .flatMap(result -> typeCheck(result, ResultType.BOOLEAN, -1))
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
      final Expression expression, final long scopeKey, final String tenantId) {
    return evaluateExpressionAsEither(expression, scopeKey, tenantId)
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
   * @throws EvaluationException if expression evaluation failed
   */
  public Either<Failure, ZonedDateTime> evaluateDateTimeExpression(
      final Expression expression, final Long scopeKey, final String tenantId) {
    return evaluateDateTimeExpression(expression, scopeKey, tenantId, false)
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
      final Expression expression,
      final Long scopeKey,
      final String tenantId,
      final boolean isNullable) {
    final var dateTimeResultTypes =
        isNullable ? NULLABLE_DATE_TIME_RESULT_TYPES : DATE_TIME_RESULT_TYPES;
    return evaluateExpressionAsEither(expression, scopeKey, tenantId)
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
   */
  public Either<Failure, DirectBuffer> evaluateAnyExpressionToBuffer(
      final Expression expression, final long scopeKey, final String tenantId) {
    final var evaluationResult = evaluateExpressionAsEither(expression, scopeKey, tenantId);
    return evaluationResult.map(EvaluationResult::toBuffer);
  }

  /**
   * Evaluates the given expression and returns the raw {@link EvaluationResult}, regardless of its
   * type.
   *
   * <p>This method is useful when the caller needs access to the full evaluation result, including
   * type information and any warnings, rather than a specific typed value.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @param tenantId the tenant owning the scope, used to resolve variables in multi-tenant setups
   * @return either the evaluation result, or a failure if the evaluation fails
   */
  public Either<Failure, EvaluationResult> evaluateAnyExpression(
      final Expression expression, final long scopeKey, final String tenantId) {
    return evaluateExpressionAsEither(expression, scopeKey, tenantId);
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
      final Expression expression, final long scopeKey, final String tenantId) {
    final var evaluationResult = evaluateExpressionAsEither(expression, scopeKey, tenantId);
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
      final Expression expression, final long scopeKey, final String tenantId) {
    final var evaluationResult = evaluateExpressionAsEither(expression, scopeKey, tenantId);
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
   */
  public Either<Failure, String> evaluateMessageCorrelationKeyExpression(
      final Expression expression, final long scopeKey, final String tenantId) {
    final var expectedTypes = Set.of(ResultType.STRING, ResultType.NUMBER);
    return evaluateExpressionAsEither(expression, scopeKey, tenantId)
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
   */
  public Either<Failure, Integer> evaluateIntegerExpression(
      final Expression expression, final long scopeKey, final String tenantId) {
    return evaluateExpressionAsEither(expression, scopeKey, tenantId)
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
   */
  public Either<Failure, DirectBuffer> evaluateVariableMappingExpression(
      final Expression expression, final long scopeKey, final String tenantId) {
    return evaluateExpressionAsEither(expression, scopeKey, tenantId)
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

  /**
   * Evaluates an expression within the appropriate evaluation context based on the provided scope
   * key and tenant identifier.
   *
   * <p>The evaluation context determines which variables are available during expression
   * evaluation. The context is selected based on the following rules:
   *
   * <h3>Case 1: No scoping required ({@code variableScopeKey < 0} and tenant is null/empty)</h3>
   *
   * <p>When there is no variable scope key (indicated by a negative value, typically {@code -1})
   * and no tenant identifier, the expression is evaluated against the base {@link
   * ScopedEvaluationContext} without any additional scoping. This is used for evaluating
   * expressions that don't require access to process instance variables or tenant-specific cluster
   * variables (e.g., static expressions, globally scoped cluster variables or expressions using
   * only literal values).
   *
   * <h3>Case 2: Tenant-scoped only ({@code variableScopeKey < 0} with a valid tenant)</h3>
   *
   * <p>When there is no variable scope key but a tenant identifier is provided, the context is
   * scoped to the tenant via {@link ScopedEvaluationContext#tenantScoped(String)}. This allows
   * access to tenant-specific cluster variables without binding to a specific process instance.
   * This case is typically used when evaluating expressions outside of a process instance context
   * but still within a tenant boundary.
   *
   * <h3>Case 3: Process and tenant scoped ({@code variableScopeKey >= 0})</h3>
   *
   * <p>When a valid variable scope key is provided (non-negative), the context must be scoped to
   * both the process instance AND the tenant. The tenant identifier is always required in this
   * case. The context is first scoped to the process via {@link
   * ScopedEvaluationContext#processScoped(long)}, then to the tenant via {@link
   * ScopedEvaluationContext#tenantScoped(String)}. This enables access to process instance
   * variables as well as tenant-specific cluster variables.
   *
   * @param expression the FEEL expression to evaluate
   * @param variableScopeKey the scope key for variable resolution (typically a process instance or
   *     element instance key); use {@code -1} if no process scope is needed
   * @param tenantId the tenant identifier for tenant-scoped variable resolution; may be {@code
   *     null}, empty, or a specific tenant ID
   * @return the result of evaluating the expression
   * @see ScopedEvaluationContext#processScoped(long)
   * @see ScopedEvaluationContext#tenantScoped(String)
   */
  private EvaluationResult evaluateExpression(
      final Expression expression, final long variableScopeKey, final String tenantId) {

    final EvaluationContext context;
    if (variableScopeKey < 0 && (tenantId == null || tenantId.isEmpty())) {
      context = scopedEvaluationContext;
    } else if (variableScopeKey < 0) {
      context = scopedEvaluationContext.tenantScoped(tenantId);
    } else {
      context = scopedEvaluationContext.processScoped(variableScopeKey).tenantScoped(tenantId);
    }

    return expressionLanguage.evaluateExpression(expression, context);
  }

  private Either<Failure, EvaluationResult> evaluateExpressionAsEither(
      final Expression expression, final long variableScopeKey, final String tenantId) {
    final var result = evaluateExpression(expression, variableScopeKey, tenantId);
    return result.isFailure()
        ? Either.left(createFailureMessage(result, result.getFailureMessage(), variableScopeKey))
        : Either.right(result);
  }

  private Either<Failure, EvaluationResult> evaluateExpressionAsEither(
      final Expression expression, final EvaluationContext context) {
    final var result = expressionLanguage.evaluateExpression(expression, context);
    return result.isFailure()
        ? Either.left(createFailureMessage(result, result.getFailureMessage(), -1))
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
  }
}
