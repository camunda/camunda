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
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.JobWorkerProperties;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.Optional;

public final class TaskDefinitionTransformer {

  public void transform(
      final ExecutableJobWorkerElement element,
      final TransformContext context,
      final ZeebeTaskDefinition taskDefinition) {

    if (taskDefinition == null) {
      return;
    }

    final var jobWorkerProperties =
        Optional.ofNullable(element.getJobWorkerProperties()).orElse(new JobWorkerProperties());
    element.setJobWorkerProperties(jobWorkerProperties);

    final ExpressionLanguage expressionLanguage = context.getExpressionLanguage();
    final Expression jobTypeExpression =
        expressionLanguage.parseExpression(taskDefinition.getType());

    jobWorkerProperties.setType(jobTypeExpression);

    final Expression retriesExpression =
        expressionLanguage.parseExpression(taskDefinition.getRetries());

    jobWorkerProperties.setRetries(retriesExpression);
  }
}
