/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ResultType;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSignal;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.Signal;

public final class SignalTransformer implements ModelElementTransformer<Signal> {

  @Override
  public Class<Signal> getType() {
    return Signal.class;
  }

  @Override
  public void transform(final Signal element, final TransformContext context) {

    final String id = element.getId();
    final ExpressionLanguage expressionLanguage = context.getExpressionLanguage();

    final ExecutableSignal executableElement = new ExecutableSignal(id);

    if (element.getName() != null) {
      final Expression signalNameExpression = expressionLanguage.parseExpression(element.getName());

      executableElement.setSignalNameExpression(signalNameExpression);

      if (signalNameExpression.isStatic()) {
        final EvaluationResult signalNameResult =
            expressionLanguage.evaluateExpression(signalNameExpression, EvaluationContext.empty());

        if (signalNameResult.getType() == ResultType.STRING) {
          final String signalName = signalNameResult.getString();
          executableElement.setSignalName(signalName);
        }
      }

      context.addSignal(executableElement);
    }
  }
}
