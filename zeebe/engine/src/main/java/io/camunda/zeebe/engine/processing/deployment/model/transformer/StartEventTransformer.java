/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ResultType;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMessage;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSignal;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;

public final class StartEventTransformer implements ModelElementTransformer<StartEvent> {

  @Override
  public Class<StartEvent> getType() {
    return StartEvent.class;
  }

  @Override
  public void transform(final StartEvent element, final TransformContext context) {
    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableStartEvent startEvent =
        process.getElementById(element.getId(), ExecutableStartEvent.class);

    startEvent.setInterrupting(element.isInterrupting());

    startEvent.setEventType(BpmnEventType.NONE);

    if (startEvent.isMessage()) {
      startEvent.setEventType(BpmnEventType.MESSAGE);
    } else if (startEvent.isTimer()) {
      startEvent.setEventType(BpmnEventType.TIMER);
    } else if (startEvent.isError()) {
      startEvent.setEventType(BpmnEventType.ERROR);
    } else if (startEvent.isEscalation()) {
      startEvent.setEventType(BpmnEventType.ESCALATION);
    } else if (startEvent.isSignal()) {
      startEvent.setEventType(BpmnEventType.SIGNAL);
    } else if (startEvent.isCompensation()) {
      startEvent.setEventType(BpmnEventType.COMPENSATION);
    }

    if (element.getScope() instanceof FlowNode) {
      final FlowNode scope = (FlowNode) element.getScope();

      final ExecutableFlowElementContainer subprocess =
          process.getElementById(scope.getId(), ExecutableFlowElementContainer.class);
      subprocess.addStartEvent(startEvent);
    } else {
      // top-level start event
      process.addStartEvent(startEvent);
    }

    if (startEvent.isMessage() && element.getScope() instanceof Process) {
      evaluateMessageNameExpression(startEvent, context);
    }

    if (startEvent.isSignal() && element.getScope() instanceof Process) {
      evaluateSignalNameExpression(startEvent, context);
    }
  }

  /**
   * Evaluates the message name expression of the message. For start events, there are no variables
   * available, so only static expressions or expressions based on literals are valid
   *
   * @param startEvent the start event; must not be {@code null}
   * @param context the transformation context; must not be {@code null}
   * @throws IllegalStateException thrown if either the evaluation failed or the result of the
   *     evaluation was not a String
   */
  private void evaluateMessageNameExpression(
      final ExecutableStartEvent startEvent, final TransformContext context) {
    final ExecutableMessage message = startEvent.getMessage();

    if (message.getMessageName().isEmpty()) {
      final ExpressionLanguage expressionLanguage = context.getExpressionLanguage();

      final EvaluationResult messageNameResult =
          expressionLanguage.evaluateExpression(
              message.getMessageNameExpression(), variable -> null);

      if (messageNameResult.isFailure()) {
        throw new IllegalStateException(
            String.format(
                "Error while evaluating '%s': %s",
                message.getMessageNameExpression(), messageNameResult.getFailureMessage()));
      } else if (messageNameResult.getType() == ResultType.STRING) {
        final String messageName = messageNameResult.getString();
        message.setMessageName(messageName);
      } else {
        throw new IllegalStateException(
            String.format(
                "Expected FEEL expression or static value of '%s' of type STRING, but was: %s",
                messageNameResult.getExpression(), messageNameResult.getType().name()));
      }
    }
  }

  /**
   * Evaluates the signal name expression of the signal. For start events, there are no variables
   * available, so only static expressions or expressions based on literals are valid
   *
   * @param startEvent the start event; must not be {@code null}
   * @param context the transformation context; must not be {@code null}
   * @throws IllegalStateException thrown if either the evaluation failed or the result of the
   *     evaluation was not a String
   */
  private void evaluateSignalNameExpression(
      final ExecutableStartEvent startEvent, final TransformContext context) {
    final ExecutableSignal signal = startEvent.getSignal();

    if (signal.getSignalName().isEmpty()) {
      final ExpressionLanguage expressionLanguage = context.getExpressionLanguage();

      final EvaluationResult signalNameResult =
          expressionLanguage.evaluateExpression(signal.getSignalNameExpression(), variable -> null);

      if (signalNameResult.isFailure()) {
        throw new IllegalStateException(
            String.format(
                "Error while evaluating '%s': %s",
                signal.getSignalNameExpression(), signalNameResult.getFailureMessage()));
      } else if (signalNameResult.getType() == ResultType.STRING) {
        final String signalName = signalNameResult.getString();
        signal.setSignalName(signalName);
      } else {
        throw new IllegalStateException(
            String.format(
                "Expected FEEL expression or static value of '%s' of type STRING, but was: %s",
                signalNameResult.getExpression(), signalNameResult.getType().name()));
      }
    }
  }
}
