/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow;

import static io.zeebe.util.EnsureUtil.ensureGreaterThan;

import io.zeebe.el.EvaluationContext;
import io.zeebe.el.EvaluationResult;
import io.zeebe.el.Expression;
import io.zeebe.el.ExpressionLanguage;
import io.zeebe.el.ResultType;
import io.zeebe.engine.processor.workflow.message.MessageCorrelationKeyContext;
import io.zeebe.engine.processor.workflow.message.MessageCorrelationKeyException;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.model.bpmn.util.time.Interval;
import io.zeebe.protocol.record.value.ErrorType;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ExpressionProcessor {

  private static final EvaluationContext EMPTY_EVALUATION_CONTEXT = x -> null;

  private final DirectBuffer resultView = new UnsafeBuffer();

  private final ExpressionLanguage expressionLanguage;
  private final VariableStateEvaluationContext evaluationContext;

  public ExpressionProcessor(
      final ExpressionLanguage expressionLanguage, final VariablesState variablesState) {
    this.expressionLanguage = expressionLanguage;

    evaluationContext = new VariableStateEvaluationContext(variablesState);
  }

  /**
   * Evaluates the given expression and returns the result as string wrapped in {@link
   * DirectBuffer}. If the evaluation fails or the result is not a string then an incident is
   * raised.
   *
   * @param expression the expression to evaluate
   * @param context the element context to load the variables from
   * @return the evaluation result as buffer, or {@link Optional#empty()} if an incident is raised
   */
  public Optional<DirectBuffer> evaluateStringExpression(
      final Expression expression, final BpmnStepContext<?> context) {

    final var evaluationResult = evaluateExpression(expression, context.getKey());
    return failureCheck(evaluationResult, ErrorType.EXTRACT_VALUE_ERROR, context)
        .flatMap(
            result -> typeCheck(result, ResultType.STRING, ErrorType.EXTRACT_VALUE_ERROR, context))
        .map(EvaluationResult::getString)
        .map(this::wrapResult);
  }

  /**
   * Evaluates the given expression and returns the result as string. If the evaluation fails or the
   * result is not a string then an exception is thrown.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return the evaluation result as string
   * @throws EvaluationException if expression evaluation failed
   */
  public String evaluateStringExpression(final Expression expression, final long scopeKey) {

    final var evaluationResult = evaluateExpression(expression, scopeKey);
    if (evaluationResult.isFailure()) {
      throw new EvaluationException(evaluationResult.getFailureMessage());
    }
    if (!evaluationResult.getType().equals(ResultType.STRING)) {
      throw new EvaluationException(
          String.format(
              "Expected result of the expression '%s' to be '%s', but was '%s'.",
              evaluationResult.getExpression(), ResultType.STRING, evaluationResult.getType()));
    }
    return evaluationResult.getString();
  }

  /**
   * Evaluates the given expression and returns the result as long. If the evaluation fails or the
   * result is not a number then an incident is raised.
   *
   * @param expression the expression to evaluate
   * @param context the element context to load the variables from
   * @return the evaluation result as long, or {@link Optional#empty()} if an incident is raised
   */
  public Optional<Long> evaluateLongExpression(
      final Expression expression, final BpmnStepContext<?> context) {

    final var evaluationResult = evaluateExpression(expression, context.getKey());
    return failureCheck(evaluationResult, ErrorType.EXTRACT_VALUE_ERROR, context)
        .flatMap(
            result -> typeCheck(result, ResultType.NUMBER, ErrorType.EXTRACT_VALUE_ERROR, context))
        .map(EvaluationResult::getNumber)
        .map(Number::longValue);
  }

  /**
   * Evaluates the given expression and returns the result as boolean. If the evaluation fails or
   * the result is not a boolean then an incident is raised.
   *
   * @param expression the expression to evaluate
   * @param context the element context to load the variables from
   * @return the evaluation result as boolean, or {@link Optional#empty()} if an incident is raised
   */
  public Optional<Boolean> evaluateBooleanExpression(
      final Expression expression, final BpmnStepContext<?> context) {

    final var evaluationResult = evaluateExpression(expression, context.getKey());
    return failureCheck(evaluationResult, ErrorType.EXTRACT_VALUE_ERROR, context)
        .flatMap(
            result -> typeCheck(result, ResultType.BOOLEAN, ErrorType.EXTRACT_VALUE_ERROR, context))
        .map(EvaluationResult::getBoolean);
  }

  /**
   * Evaluates the given expression and returns the result as an Interval. If the evaluation fails
   * or the result is not an interval then an exception is thrown.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return the evaluation result as interval
   * @throws EvaluationException if expression evaluation failed
   */
  public Interval evaluateIntervalExpression(final Expression expression, final long scopeKey) {
    final var result = evaluateExpression(expression, scopeKey);
    if (result.isFailure()) {
      throw new EvaluationException(result.getFailureMessage());
    }
    switch (result.getType()) {
      case DURATION:
        return new Interval(result.getDuration());
      case PERIOD:
        return new Interval(result.getPeriod());
      case STRING:
        try {
          return Interval.parse(result.getString());
        } catch (DateTimeParseException e) {
          throw new EvaluationException(
              String.format(
                  "Expected result of the expression '%s' to be parsed to a duration, but was '%s'",
                  expression.getExpression(), result.getString()),
              e);
        }
      default:
        final var expected = List.of(ResultType.DURATION, ResultType.PERIOD, ResultType.STRING);
        throw new EvaluationException(
            String.format(
                "Expected result of the expression '%s' to be one of '%s', but was '%s'",
                expression.getExpression(), expected, result.getType()));
    }
  }

  /**
   * Evaluates the given expression and returns the result as ZonedDateTime. If the evaluation fails
   * or the result is not a ZonedDateTime then an exception is thrown.
   *
   * @param expression the expression to evaluate
   * @param scopeKey the scope to load the variables from (a negative key is intended to imply an
   *     empty variable context)
   * @return the evaluation result as ZonedDateTime
   * @throws EvaluationException if expression evaluation failed
   */
  public ZonedDateTime evaluateDateTimeExpression(
      final Expression expression, final Long scopeKey) {
    final var result = evaluateExpression(expression, scopeKey);
    if (result.isFailure()) {
      throw new EvaluationException(result.getFailureMessage());
    }
    if (result.getType() == ResultType.DATE_TIME) {
      return result.getDateTime();
    }
    if (result.getType() == ResultType.STRING) {
      return ZonedDateTime.parse(result.getString());
    }
    final var expected = List.of(ResultType.DATE_TIME, ResultType.STRING);
    throw new EvaluationException(
        String.format(
            "Expected result of the expression '%s' to be one of '%s', but was '%s'",
            expression.getExpression(), expected, result.getType()));
  }

  /**
   * Evaluates the given expression and returns the result no matter the type.
   *
   * @param expression the expression to evaluate
   * @param context the element context to load the variables from
   * @return the evaluation result as buffer, or {@link Optional#empty()} if an incident is raised
   */
  public Optional<DirectBuffer> evaluateAnyExpression(
      final Expression expression, final BpmnStepContext<?> context) {
    final var evaluationResult = evaluateExpression(expression, context.getKey());
    return failureCheck(evaluationResult, ErrorType.EXTRACT_VALUE_ERROR, context)
        .map(EvaluationResult::toBuffer);
  }

  /**
   * Evaluates the given expression and returns the result as a list. The entries of the list are
   * encoded in MessagePack and can have any type.
   *
   * @param expression the expression to evaluate
   * @param context the element context to load the variables from
   * @return the evaluation result as a list, or {@link Optional#empty()} if an incident is raised
   */
  public Optional<List<DirectBuffer>> evaluateArrayExpression(
      final Expression expression, final BpmnStepContext<?> context) {
    final var evaluationResult = evaluateExpression(expression, context.getKey());
    return failureCheck(evaluationResult, ErrorType.EXTRACT_VALUE_ERROR, context)
        .flatMap(
            result -> typeCheck(result, ResultType.ARRAY, ErrorType.EXTRACT_VALUE_ERROR, context))
        .map(EvaluationResult::getList);
  }

  /**
   * Evaluates the given expression and returns the result as String. If the evaluation fails or the
   * result is not a string or number (the latter of which is automatically converted to a string),
   * then an exception is thrown.
   *
   * @param expression the expression to evaluate
   * @param context context object used to determine the variable scope key;
   * @return the evaluation result as String
   * @throws MessageCorrelationKeyException if the evaluation fails or the result is not a string or
   *     number
   */
  public String evaluateMessageCorrelationKeyExpression(
      final Expression expression, final MessageCorrelationKeyContext context) {

    final var evaluationResult = evaluateExpression(expression, context.getVariablesScopeKey());

    final var resultHandler = new CorrelationKeyResultHandler(context);
    return resultHandler.apply(evaluationResult);
  }

  /**
   * Evaluates the given expression of a variable mapping and returns the result as buffer. If the
   * evaluation fails or the result is not a context then an incident is raised.
   *
   * @param expression the expression to evaluate
   * @param context the element context to load the variables from
   * @return the evaluation result as buffer, or {@link Optional#empty()} if an incident is raised
   */
  public Optional<DirectBuffer> evaluateVariableMappingExpression(
      final Expression expression, final BpmnStepContext<?> context) {

    final var evaluationResult = evaluateExpression(expression, context.getKey());
    return failureCheck(evaluationResult, ErrorType.IO_MAPPING_ERROR, context)
        .flatMap(
            result -> typeCheck(result, ResultType.OBJECT, ErrorType.IO_MAPPING_ERROR, context))
        .map(EvaluationResult::toBuffer);
  }

  private Optional<EvaluationResult> failureCheck(
      final EvaluationResult result, final ErrorType errorType, final BpmnStepContext<?> context) {

    if (result.isFailure()) {
      context.raiseIncident(errorType, result.getFailureMessage());
      return Optional.empty();

    } else {
      return Optional.of(result);
    }
  }

  private Optional<EvaluationResult> typeCheck(
      final EvaluationResult result,
      final ResultType expectedResultType,
      final ErrorType errorType,
      final BpmnStepContext<?> context) {

    if (result.getType() != expectedResultType) {
      context.raiseIncident(
          errorType,
          String.format(
              "Expected result of the expression '%s' to be '%s', but was '%s'.",
              result.getExpression(), expectedResultType, result.getType()));
      return Optional.empty();

    } else {
      return Optional.of(result);
    }
  }

  private EvaluationResult evaluateExpression(
      final Expression expression, final long variableScopeKey) {

    final EvaluationContext context;
    if (variableScopeKey < 0) {
      context = EMPTY_EVALUATION_CONTEXT;
    } else {
      evaluationContext.variableScopeKey = variableScopeKey;
      context = evaluationContext;
    }

    return expressionLanguage.evaluateExpression(expression, context);
  }

  private DirectBuffer wrapResult(final String result) {
    resultView.wrap(result.getBytes());
    return resultView;
  }

  public static final class EvaluationException extends RuntimeException {

    public EvaluationException(final String message) {
      super(message);
    }

    public EvaluationException(final String message, final Throwable cause) {
      super(message, cause);
    }

    public EvaluationException(final Throwable cause) {
      super(cause);
    }

    public EvaluationException(
        final String message,
        final Throwable cause,
        final boolean enableSuppression,
        final boolean writableStackTrace) {
      super(message, cause, enableSuppression, writableStackTrace);
    }
  }

  protected static final class CorrelationKeyResultHandler
      implements Function<EvaluationResult, String> {

    private final MessageCorrelationKeyContext context;

    protected CorrelationKeyResultHandler(final MessageCorrelationKeyContext context) {
      this.context = context;
    }

    @Override
    public String apply(final EvaluationResult evaluationResult) {
      if (evaluationResult.isFailure()) {
        throw new MessageCorrelationKeyException(context, evaluationResult.getFailureMessage());
      }
      if (evaluationResult.getType() == ResultType.STRING) {
        return evaluationResult.getString();
      } else if (evaluationResult.getType() == ResultType.NUMBER) {

        final Number correlationKeyNumber = evaluationResult.getNumber();

        return Long.toString(correlationKeyNumber.longValue());
      } else {
        final String failureMessage =
            String.format(
                "Failed to extract the correlation key for '%s': The value must be either a string or a number, but was %s.",
                evaluationResult.getExpression(), evaluationResult.getType().toString());
        throw new MessageCorrelationKeyException(context, failureMessage);
      }
    }
  }

  private static class VariableStateEvaluationContext implements EvaluationContext {

    private final DirectBuffer variableNameBuffer = new UnsafeBuffer();

    private final VariablesState variablesState;

    private long variableScopeKey;

    public VariableStateEvaluationContext(final VariablesState variablesState) {
      this.variablesState = variablesState;
    }

    @Override
    public DirectBuffer getVariable(final String variableName) {
      ensureGreaterThan("variable scope key", variableScopeKey, 0);

      variableNameBuffer.wrap(variableName.getBytes());

      return variablesState.getVariable(variableScopeKey, variableNameBuffer);
    }
  }
}
