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

    final var evaluationResult = evaluateExpression(expression, context.getKey());
    return failureCheck(evaluationResult, ErrorType.EXTRACT_VALUE_ERROR, context)
        .flatMap(
            result -> typeCheck(result, ResultType.STRING, ErrorType.EXTRACT_VALUE_ERROR, context))
        .map(EvaluationResult::getString)
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

    final var evaluationResult = evaluateExpression(expression, context.getKey());
    return failureCheck(evaluationResult, ErrorType.EXTRACT_VALUE_ERROR, context)
        .flatMap(
            result -> typeCheck(result, ResultType.BOOLEAN, ErrorType.EXTRACT_VALUE_ERROR, context))
        .map(EvaluationResult::getBoolean);
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

    evaluationContext.variableScopeKey = variableScopeKey;

    return expressionLanguage.evaluateExpression(expression, evaluationContext);
  }

  private DirectBuffer wrapResult(final String result) {
    resultView.wrap(result.getBytes());
    return resultView;
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
