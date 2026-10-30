/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.deployment.model.element.ConsentDefinition;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeConsent;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeRelationship;
import io.camunda.zeebe.protocol.record.value.ConsentGround;

/** Turns {@code zeebe:consent} or {@code zeebe:relationship} into the task's consent definition. */
public final class ConsentDefinitionTransformer {

  public void transform(
      final ExecutableJobWorkerElement element,
      final TransformContext context,
      final ZeebeConsent consent,
      final ZeebeRelationship relationship) {
    final ExpressionLanguage expressionLanguage = context.getExpressionLanguage();
    if (consent != null) {
      element.setConsentDefinition(
          new ConsentDefinition(
              ConsentGround.CONSENT,
              consent.getConnection(),
              consent.getOperation(),
              parseOptional(expressionLanguage, consent.getSubject()),
              null,
              parseOptional(expressionLanguage, consent.getChange())));
    } else if (relationship != null) {
      element.setConsentDefinition(
          new ConsentDefinition(
              ConsentGround.RELATIONSHIP,
              relationship.getConnection(),
              relationship.getOperation(),
              null,
              parseOptional(expressionLanguage, relationship.getTarget()),
              parseOptional(expressionLanguage, relationship.getChange())));
    }
  }

  private static Expression parseOptional(
      final ExpressionLanguage expressionLanguage, final String expression) {
    return expression == null || expression.isBlank()
        ? null
        : expressionLanguage.parseExpression(expression);
  }
}
