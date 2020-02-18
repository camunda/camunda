/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow;

import io.zeebe.el.EvaluationResult;
import io.zeebe.el.Expression;
import io.zeebe.el.ExpressionLanguage;
import io.zeebe.el.ResultType;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.protocol.record.value.ErrorType;
import java.util.Optional;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ExpressionProcessor {

  private static final DirectBuffer NO_VARIABLES = new UnsafeBuffer();

  private final DirectBuffer resultView = new UnsafeBuffer();

  private final ExpressionLanguage expressionLanguage;
  private final VariablesState variablesState;

  public ExpressionProcessor(
      final ExpressionLanguage expressionLanguage, final VariablesState variablesState) {
    this.expressionLanguage = expressionLanguage;
    this.variablesState = variablesState;
  }

  /**
   * Evaluates the given expression and returns the result as string. If the evaluation fails or the
   * result is not a string then an incident is raised.
   *
   * @param expression the expression to evaluate
   * @param context the element context to load the variables from
   * @return the evaluation result as buffer, or {@link Optional#empty()} if an incident is raised
   */
  public Optional<DirectBuffer> evaluateStringExpression(
      final Expression expression, final BpmnStepContext<?> context) {

    return evaluateExpression(expression, context, ResultType.STRING, EvaluationResult::getString)
        .map(this::wrapResult);
  }

  private <T> Optional<T> evaluateExpression(
      final Expression expression,
      final BpmnStepContext<?> context,
      final ResultType expectedResultType,
      final Function<EvaluationResult, T> resultExtractor) {

    final var evaluationResult = evaluateExpression(expression, context.getKey());

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

  private EvaluationResult evaluateExpression(
      final Expression expression, final long variableScopeKey) {

    if (expression.isStatic()) {
      return expressionLanguage.evaluateExpression(expression, NO_VARIABLES);

    } else {
      final var variables = variablesState.getVariablesAsDocument(variableScopeKey);
      return expressionLanguage.evaluateExpression(expression, variables);
    }
  }

  private DirectBuffer wrapResult(final String result) {
    resultView.wrap(result.getBytes());
    return resultView;
  }
}
