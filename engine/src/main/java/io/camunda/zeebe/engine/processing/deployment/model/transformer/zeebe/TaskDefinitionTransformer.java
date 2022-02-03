/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.deployment.model.element.JobWorkerProperties;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;

public final class TaskDefinitionTransformer {

  public void transform(
      final FlowElement element,
      final JobWorkerProperties jobWorkerProperties,
      final TransformContext context) {
    final ZeebeTaskDefinition taskDefinition =
        element.getSingleExtensionElement(ZeebeTaskDefinition.class);

    final ExpressionLanguage expressionLanguage = context.getExpressionLanguage();
    final Expression jobTypeExpression =
        expressionLanguage.parseExpression(taskDefinition.getType());

    jobWorkerProperties.setType(jobTypeExpression);

    final Expression retriesExpression =
        expressionLanguage.parseExpression(taskDefinition.getRetries());

    jobWorkerProperties.setRetries(retriesExpression);
  }
}
