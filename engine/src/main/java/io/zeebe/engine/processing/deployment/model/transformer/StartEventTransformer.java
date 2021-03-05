/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.transformer;

import io.zeebe.el.EvaluationResult;
import io.zeebe.el.ExpressionLanguage;
import io.zeebe.el.ResultType;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.processing.deployment.model.element.ExecutableMessage;
import io.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.model.bpmn.instance.StartEvent;

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
}
