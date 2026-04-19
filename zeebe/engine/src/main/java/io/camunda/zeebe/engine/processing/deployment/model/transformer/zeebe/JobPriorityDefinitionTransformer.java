/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.JobWorkerProperties;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeJobPriorityDefinition;
import java.util.Optional;

public final class JobPriorityDefinitionTransformer {

  public void transform(
      final ExecutableJobWorkerElement element,
      final TransformContext context,
      final ZeebeJobPriorityDefinition jobPriorityDefinition) {

    final Expression priorityExpression = resolvePriorityExpression(context, jobPriorityDefinition);
    if (priorityExpression == null) {
      return;
    }

    final var jobWorkerProperties =
        Optional.ofNullable(element.getJobWorkerProperties()).orElse(new JobWorkerProperties());
    element.setJobWorkerProperties(jobWorkerProperties);
    jobWorkerProperties.setJobPriority(priorityExpression);
  }

  private Expression resolvePriorityExpression(
      final TransformContext context, final ZeebeJobPriorityDefinition taskLevel) {
    if (taskLevel != null) {
      return context.getExpressionLanguage().parseExpression(taskLevel.getPriority());
    }
    return context.getCurrentProcess().getJobPriority();
  }
}
