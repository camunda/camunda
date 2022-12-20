/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableError;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEscalation;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableLink;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMessage;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSignal;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.engine.processing.timer.CronTimer;
import io.camunda.zeebe.model.bpmn.instance.CatchEvent;
import io.camunda.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EscalationEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.EventDefinition;
import io.camunda.zeebe.model.bpmn.instance.LinkEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.Signal;
import io.camunda.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.camunda.zeebe.model.bpmn.util.time.RepeatingInterval;
import io.camunda.zeebe.model.bpmn.util.time.TimeDateTimer;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.format.DateTimeParseException;

public final class CatchEventTransformer implements ModelElementTransformer<CatchEvent> {

  @Override
  public Class<CatchEvent> getType() {
    return CatchEvent.class;
  }

  @Override
  public void transform(final CatchEvent element, final TransformContext context) {
    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableCatchEventElement executableElement =
        process.getElementById(element.getId(), ExecutableCatchEventElement.class);

    if (!element.getEventDefinitions().isEmpty()) {
      transformEventDefinition(element, context, executableElement);
    }
  }

  private void transformEventDefinition(
      final CatchEvent element,
      final TransformContext context,
      final ExecutableCatchEventElement executableElement) {
    final EventDefinition eventDefinition = element.getEventDefinitions().iterator().next();
    if (eventDefinition instanceof MessageEventDefinition) {
      transformMessageEventDefinition(
          context, executableElement, (MessageEventDefinition) eventDefinition);

    } else if (eventDefinition instanceof TimerEventDefinition) {
      final var expressionLanguage = context.getExpressionLanguage();
      final var timerDefinition = (TimerEventDefinition) eventDefinition;
      transformTimerEventDefinition(expressionLanguage, executableElement, timerDefinition);

    } else if (eventDefinition instanceof ErrorEventDefinition) {
      transformErrorEventDefinition(
          context, executableElement, (ErrorEventDefinition) eventDefinition);
    } else if (eventDefinition instanceof LinkEventDefinition) {
      transformLinkEventDefinition(
          context, executableElement, (LinkEventDefinition) eventDefinition);
    } else if (eventDefinition instanceof EscalationEventDefinition) {
      transformEscalationEventDefinition(
          context, executableElement, (EscalationEventDefinition) eventDefinition);
    } else if (eventDefinition instanceof SignalEventDefinition) {
      transformSignalEventDefinition(
          context, executableElement, (SignalEventDefinition) eventDefinition);
    }
  }

  private void transformMessageEventDefinition(
      final TransformContext context,
      final ExecutableCatchEventElement executableElement,
      final MessageEventDefinition messageEventDefinition) {

    final Message message = messageEventDefinition.getMessage();
    final ExecutableMessage executableMessage = context.getMessage(message.getId());
    executableElement.setMessage(executableMessage);
    executableElement.setEventType(BpmnEventType.MESSAGE);
  }

  private void transformTimerEventDefinition(
      final ExpressionLanguage expressionLanguage,
      final ExecutableCatchEventElement executableElement,
      final TimerEventDefinition timerEventDefinition) {

    executableElement.setEventType(BpmnEventType.TIMER);

    final Expression expression;
    if (timerEventDefinition.getTimeDuration() != null) {
      final String duration = timerEventDefinition.getTimeDuration().getTextContent();
      expression = expressionLanguage.parseExpression(duration);
      executableElement.setTimerFactory(
          (expressionProcessor, scopeKey) ->
              expressionProcessor
                  .evaluateIntervalExpression(expression, scopeKey)
                  .map(interval -> new RepeatingInterval(1, interval)));

    } else if (timerEventDefinition.getTimeCycle() != null) {
      final String cycle = timerEventDefinition.getTimeCycle().getTextContent();
      expression = expressionLanguage.parseExpression(cycle);
      executableElement.setTimerFactory(
          (expressionProcessor, scopeKey) -> {
            try {
              return expressionProcessor
                  .evaluateStringExpression(expression, scopeKey)
                  .map(
                      text -> {
                        if (text.startsWith("R")) {
                          return RepeatingInterval.parse(text);
                        }
                        return CronTimer.parse(text);
                      });
            } catch (final DateTimeParseException e) {
              // todo(#4323): replace this caught exception with Either
              return Either.left(
                  new Failure(e.getMessage(), ErrorType.EXTRACT_VALUE_ERROR, scopeKey));
            }
          });

    } else if (timerEventDefinition.getTimeDate() != null) {
      final String timeDate = timerEventDefinition.getTimeDate().getTextContent();
      expression = expressionLanguage.parseExpression(timeDate);
      executableElement.setTimerFactory(
          (expressionProcessor, scopeKey) ->
              expressionProcessor
                  .evaluateDateTimeExpression(expression, scopeKey)
                  .map(TimeDateTimer::new));
    }
  }

  private void transformErrorEventDefinition(
      final TransformContext context,
      final ExecutableCatchEventElement executableElement,
      final ErrorEventDefinition errorEventDefinition) {

    final var error = errorEventDefinition.getError();
    final ExecutableError executableError;

    // If 'errorRef' is omitted, an empty error without errorCode will be created to facilitate
    // finding the corresponding catch event
    if (error == null) {
      executableError = new ExecutableError("");
      executableError.setErrorCode(BufferUtil.wrapString(""));
    } else {
      executableError = context.getError(error.getId());
    }
    executableElement.setError(executableError);
    executableElement.setEventType(BpmnEventType.ERROR);
  }

  public void transformLinkEventDefinition(
      final TransformContext context,
      final ExecutableCatchEventElement executableElement,
      final LinkEventDefinition linkEventDefinition) {

    executableElement.setLink(true);
    executableElement.setEventType(BpmnEventType.LINK);

    final var link = new ExecutableLink(linkEventDefinition.getId());
    link.setName(BufferUtil.wrapString(linkEventDefinition.getName()));
    link.setCatchEvent(executableElement);
    context.addLink(link);
  }

  private void transformEscalationEventDefinition(
      final TransformContext context,
      final ExecutableCatchEventElement executableElement,
      final EscalationEventDefinition escalationEventDefinition) {

    final var escalation = escalationEventDefinition.getEscalation();
    final ExecutableEscalation executableEscalation;

    // If 'escalationRef' is omitted, an empty escalation without escalationCode will be created
    // to facilitate finding the corresponding catch event
    if (escalation == null) {
      executableEscalation = new ExecutableEscalation("");
      executableEscalation.setEscalationCode(BufferUtil.wrapString(""));
    } else {
      executableEscalation = context.getEscalation(escalation.getId());
    }
    executableElement.setEscalation(executableEscalation);
    executableElement.setEventType(BpmnEventType.ESCALATION);
  }

  private void transformSignalEventDefinition(
      final TransformContext context,
      final ExecutableCatchEventElement executableElement,
      final SignalEventDefinition signalEventDefinition) {

    final Signal signal = signalEventDefinition.getSignal();
    final ExecutableSignal executableSignal = context.getSignal(signal.getId());
    executableElement.setSignal(executableSignal);
    executableElement.setEventType(BpmnEventType.SIGNAL);
  }
}
