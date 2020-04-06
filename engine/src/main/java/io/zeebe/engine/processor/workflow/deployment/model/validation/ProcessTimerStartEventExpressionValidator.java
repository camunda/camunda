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
import io.zeebe.engine.processor.workflow.ExpressionProcessor;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.zeebe.model.bpmn.util.time.RepeatingInterval;
import io.zeebe.model.bpmn.util.time.TimeDateTimer;
import io.zeebe.model.bpmn.util.time.Timer;
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
    try {
      final var timer = tryEvaluateTimer(timerEventDefinition);
    } catch (RuntimeException e) {
      validationResultCollector.addError(0, String.format(ERROR_MESSAGE_TEMPLATE, e.getMessage()));
    }
  }

  /**
   * Try to create a timer from the timer event definition by evaluating its time expression.
   *
   * @param timerEventDefinition The definition specifying the timer expression to evaluate
   * @throws RuntimeException when the expression could not be evaluated or when the expression
   *     result could not be used to create a timer.
   */
  private Timer tryEvaluateTimer(final TimerEventDefinition timerEventDefinition) {
    // There are no variables when there is no process instance yet,
    // we use a negative scope key to indicate this
    final long scopeKey = -1;
    final Expression expression;
    if (timerEventDefinition.getTimeDuration() != null) {
      final String duration = timerEventDefinition.getTimeDuration().getTextContent();
      expression = expressionLanguage.parseExpression(duration);
      return new RepeatingInterval(
          1, expressionProcessor.evaluateIntervalExpression(expression, scopeKey));

    } else if (timerEventDefinition.getTimeCycle() != null) {
      final String cycle = timerEventDefinition.getTimeCycle().getTextContent();
      expression = expressionLanguage.parseExpression(cycle);
      return RepeatingInterval.parse(
          expressionProcessor.evaluateStringExpression(expression, scopeKey));

    } else if (timerEventDefinition.getTimeDate() != null) {
      final String timeDate = timerEventDefinition.getTimeDate().getTextContent();
      expression = expressionLanguage.parseExpression(timeDate);
      return new TimeDateTimer(
          expressionProcessor.evaluateDateTimeExpression(expression, scopeKey));
    }
    throw new IllegalStateException(
        "Expected timer event to have a time duration, time cycle or time date definition, but found none");
  }
}
