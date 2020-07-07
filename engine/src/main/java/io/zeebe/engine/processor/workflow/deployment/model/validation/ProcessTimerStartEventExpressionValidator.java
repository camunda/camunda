/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.validation;

import io.zeebe.el.Expression;
import io.zeebe.el.ExpressionLanguage;
import io.zeebe.engine.processor.Failure;
import io.zeebe.engine.processor.workflow.ExpressionProcessor;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.zeebe.model.bpmn.util.time.RepeatingInterval;
import io.zeebe.model.bpmn.util.time.TimeDateTimer;
import io.zeebe.model.bpmn.util.time.Timer;
import io.zeebe.util.Either;
import java.time.format.DateTimeParseException;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ProcessTimerStartEventExpressionValidator
    implements ModelElementValidator<StartEvent> {

  private static final String ERROR_MESSAGE_TEMPLATE =
      "Expected a valid timer expression for start event, but encountered the following error: %s";
  private final ExpressionLanguage expressionLanguage;
  private final ExpressionProcessor expressionProcessor;

  public ProcessTimerStartEventExpressionValidator(
      final ExpressionLanguage expressionLanguage, final ExpressionProcessor expressionProcessor) {
    this.expressionLanguage = expressionLanguage;
    this.expressionProcessor = expressionProcessor;
  }

  @Override
  public Class<StartEvent> getElementType() {
    return StartEvent.class;
  }

  @Override
  public void validate(
      final StartEvent element, final ValidationResultCollector validationResultCollector) {
    if (!(element.getScope() instanceof Process)) {
      return;
    }
    element.getEventDefinitions().stream()
        .filter(TimerEventDefinition.class::isInstance)
        .map(TimerEventDefinition.class::cast)
        .forEach(definition -> validation(definition, validationResultCollector));
  }

  private void validation(
      final TimerEventDefinition timerEventDefinition,
      final ValidationResultCollector validationResultCollector) {
    final Either<Failure, Timer> timerOrError = tryEvaluateTimer(timerEventDefinition);
    if (timerOrError.isLeft()) {
      final var message =
          String.format(ERROR_MESSAGE_TEMPLATE, timerOrError.getLeft().getMessage());
      validationResultCollector.addError(0, message);
    }
  }

  /**
   * Try to create a timer from the timer event definition by evaluating its time expression.
   *
   * @param timerEventDefinition The definition specifying the timer expression to evaluate
   * @return either a timer or a failure when the expression could not be evaluated or when the
   *     expression result could not be used to create a timer.
   */
  private Either<Failure, Timer> tryEvaluateTimer(final TimerEventDefinition timerEventDefinition) {
    // There are no variables when there is no process instance yet,
    // we use a negative scope key to indicate this
    final long scopeKey = -1;
    final Expression expression;
    if (timerEventDefinition.getTimeDuration() != null) {
      final String duration = timerEventDefinition.getTimeDuration().getTextContent();
      expression = expressionLanguage.parseExpression(duration);
      return expressionProcessor
          .evaluateIntervalExpression(expression, scopeKey)
          .map(interval -> new RepeatingInterval(1, interval));

    } else if (timerEventDefinition.getTimeCycle() != null) {
      final String cycle = timerEventDefinition.getTimeCycle().getTextContent();
      expression = expressionLanguage.parseExpression(cycle);
      try {
        return expressionProcessor
            .evaluateStringExpression(expression, scopeKey)
            .map(RepeatingInterval::parse);
      } catch (final DateTimeParseException e) {
        // todo(#4323): replace this caught exception with Either
        return Either.left(new Failure(e.getMessage()));
      }
    } else if (timerEventDefinition.getTimeDate() != null) {
      final String timeDate = timerEventDefinition.getTimeDate().getTextContent();
      expression = expressionLanguage.parseExpression(timeDate);
      return expressionProcessor
          .evaluateDateTimeExpression(expression, scopeKey)
          .map(TimeDateTimer::new);
    }
    return Either.left(
        new Failure(
            "Expected timer event to have a time duration, time cycle or time date definition, but found none"));
  }
}
