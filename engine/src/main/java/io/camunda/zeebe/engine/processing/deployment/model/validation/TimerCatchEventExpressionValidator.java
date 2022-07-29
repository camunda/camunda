/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.timer.CronTimer;
import io.camunda.zeebe.model.bpmn.instance.CatchEvent;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.camunda.zeebe.model.bpmn.util.time.RepeatingInterval;
import io.camunda.zeebe.util.Either;
import java.time.format.DateTimeParseException;
import java.util.function.Function;
import java.util.function.Predicate;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class TimerCatchEventExpressionValidator implements ModelElementValidator<CatchEvent> {

  private static final long NO_VARIABLE_SCOPE = -1L;

  private final ExpressionLanguage expressionLanguage;
  private final ExpressionProcessor expressionProcessor;

  public TimerCatchEventExpressionValidator(
      final ExpressionLanguage expressionLanguage, final ExpressionProcessor expressionProcessor) {
    this.expressionLanguage = expressionLanguage;
    this.expressionProcessor = expressionProcessor;
  }

  @Override
  public Class<CatchEvent> getElementType() {
    return CatchEvent.class;
  }

  @Override
  public void validate(
      final CatchEvent element, final ValidationResultCollector validationResultCollector) {

    // verify static expressions only because other expression may requires a variable context
    // - except for process start events because they don't have any variable context
    final var isTimerStartEventOfProcess =
        element instanceof StartEvent && element.getScope() instanceof Process;
    final Predicate<Expression> expressionFilter =
        expression -> isTimerStartEventOfProcess || expression.isStatic();

    element.getEventDefinitions().stream()
        .filter(TimerEventDefinition.class::isInstance)
        .map(TimerEventDefinition.class::cast)
        .forEach(definition -> validation(definition, expressionFilter, validationResultCollector));
  }

  private void validation(
      final TimerEventDefinition timerEventDefinition,
      final Predicate<Expression> expressionFilter,
      final ValidationResultCollector validationResultCollector) {

    evaluateTimerExpression(timerEventDefinition, expressionFilter)
        .ifLeft(failure -> validationResultCollector.addError(0, failure.getMessage()));
  }

  private Either<Failure, ?> evaluateTimerExpression(
      final TimerEventDefinition timerEventDefinition,
      final Predicate<Expression> expressionFilter) {

    if (timerEventDefinition.getTimeDuration() != null) {
      final String duration = timerEventDefinition.getTimeDuration().getTextContent();
      final var expression = expressionLanguage.parseExpression(duration);

      if (expressionFilter.test(expression)) {
        return expressionProcessor
            .evaluateIntervalExpression(expression, NO_VARIABLE_SCOPE)
            .mapLeft(wrapFailure("duration"));
      }

    } else if (timerEventDefinition.getTimeCycle() != null) {
      final String cycle = timerEventDefinition.getTimeCycle().getTextContent();
      final var expression = expressionLanguage.parseExpression(cycle);

      if (expressionFilter.test(expression)) {
        try {
          return expressionProcessor
              .evaluateStringExpression(expression, NO_VARIABLE_SCOPE)
              .map(
                  text -> {
                    if (text.startsWith("R")) {
                      return RepeatingInterval.parse(text);
                    }
                    return CronTimer.parse(text);
                  })
              .mapLeft(wrapFailure("cycle"));
        } catch (final DateTimeParseException e) {
          final var failureDetails = new Failure(e.getMessage());
          return Either.left(failureDetails).mapLeft(wrapFailure("cycle"));
        }
      }

    } else if (timerEventDefinition.getTimeDate() != null) {
      final String timeDate = timerEventDefinition.getTimeDate().getTextContent();
      final var expression = expressionLanguage.parseExpression(timeDate);

      if (expressionFilter.test(expression)) {
        return expressionProcessor
            .evaluateDateTimeExpression(expression, NO_VARIABLE_SCOPE)
            .mapLeft(wrapFailure("date"));
      }
    }
    return Either.right(null);
  }

  private Function<Failure, Failure> wrapFailure(final String timerType) {
    return failureDetails ->
        new Failure(
            String.format(
                "Invalid timer %s expression (%s)", timerType, failureDetails.getMessage()));
  }
}
