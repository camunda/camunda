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
import io.zeebe.protocol.record.value.ErrorType;
import java.util.Optional;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ExpressionProcessor {

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

    return evaluateExpression(
            expression,
            context.getKey(),
            new TypeVerifyingIncidentRaisingHandler<>(
                ResultType.STRING, context, EvaluationResult::getString))
        .map(this::wrapResult);
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

    return evaluateExpression(
        expression,
        context.getKey(),
        new TypeVerifyingIncidentRaisingHandler<Boolean>(
            ResultType.BOOLEAN, context, EvaluationResult::getBoolean));
  }

  /**
   * Evaluates the given expression and returns the result as String. If the evaluation fails or the
   * result is not a string or number (the latter of which is automatically converted to a string),
   * then an exeption is thrown
   *
   * @param expression the expression to evaluate; must not be {@code null}
   * @param context context object used to determine the varaible scope key; must not be {@code
   *     null}
   * @return the evaluation result as String
   */
  public String evaluateMessageCorrelationKeyExpression(
      final Expression expression, final MessageCorrelationKeyContext context) {
    return evaluateExpression(
        expression, context.getVariablesScopeKey(), new CorrelationKeyResultHandler(context));
  }

  /**
   * Evaluates the given expression and passes the result to the {@code resultHandler}
   *
   * @param expression the expression to evaluate; must not be {@code null}
   * @param variableScopeKey the key to identify the variable scope which will provide the context
   *     in which the expression is evaluated
   * @param resultHandler the result handler to process the evaluation result, and convert it to the
   *     desired result type; must not be {@code null}
   * @param <T> desired result type
   * @return the result of the expression evaluation
   */
  private <T> T evaluateExpression(
      final Expression expression,
      final long variableScopeKey,
      final Function<EvaluationResult, T> resultHandler) {

    final var evaluationResult = evaluateExpression(expression, variableScopeKey);

    return resultHandler.apply(evaluationResult);
  }

  private EvaluationResult evaluateExpression(
      final Expression expression, final long variableScopeKey) {

    evaluationContext.variableScopeKey = variableScopeKey;

    return expressionLanguage.evaluateExpression(expression, evaluationContext);
  }

  private DirectBuffer wrapResult(final String result) {
    resultView.wrap(result.getBytes());
    return resultView;
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

  /**
   * This expression result handler raises an incident if the evaluation resulted in a failure or an
   * unexpected type
   *
   * @param <T> target type to which the result of the expression shall be converted
   */
  protected final class TypeVerifyingIncidentRaisingHandler<T>
      implements Function<EvaluationResult, Optional<T>> {

    final ResultType expectedResultType;
    final Function<EvaluationResult, T> resultExtractor;
    final BpmnStepContext<?> context;

    public TypeVerifyingIncidentRaisingHandler(
        final ResultType expectedResultType,
        final BpmnStepContext<?> context,
        final Function<EvaluationResult, T> resultExtractor) {
      this.expectedResultType = expectedResultType;
      this.context = context;

      this.resultExtractor = resultExtractor;
    }

    @Override
    public Optional<T> apply(final EvaluationResult evaluationResult) {
      if (evaluationResult.isFailure()) {
        context.raiseIncident(ErrorType.EXTRACT_VALUE_ERROR, evaluationResult.getFailureMessage());
        return Optional.empty();
      }

      if (evaluationResult.getType() != expectedResultType) {
        context.raiseIncident(
            ErrorType.EXTRACT_VALUE_ERROR,
            String.format(
                "Expected result of the expression '%s' to be '%s', but was '%s'.",
                evaluationResult.getExpression(), expectedResultType, evaluationResult.getType()));
        return Optional.empty();
      }

      return Optional.of(resultExtractor.apply(evaluationResult));
    }
  }
}
