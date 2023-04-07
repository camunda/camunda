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
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEscalation;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.Escalation;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class EscalationTransformer implements ModelElementTransformer<Escalation> {

  @Override
  public Class<Escalation> getType() {
    return Escalation.class;
  }

  @Override
  public void transform(final Escalation element, final TransformContext context) {
    final ExpressionLanguage expressionLanguage = context.getExpressionLanguage();
    final var escalation = new ExecutableEscalation(element.getId());

    if (element.getEscalationCode() != null) {
      final Expression escalationCodeExpression =
          expressionLanguage.parseExpression(element.getEscalationCode());

      escalation.setEscalationCodeExpression(escalationCodeExpression);
      if (escalationCodeExpression.isStatic()) {
        escalation.setEscalationCode(BufferUtil.wrapString(element.getEscalationCode()));
      }
    }
    context.addEscalation(escalation);
  }
}
