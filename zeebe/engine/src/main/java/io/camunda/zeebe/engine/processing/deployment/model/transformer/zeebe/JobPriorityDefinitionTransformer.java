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
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeJobPriorityDefinition;

public final class JobPriorityDefinitionTransformer {

  private static final String NEUTRAL_PRIORITY = "0";

  public void transform(
      final ExecutableJobWorkerElement element,
      final TransformContext context,
      final ZeebeJobPriorityDefinition priorityDefinition) {

    final var jobWorkerProperties = element.getJobWorkerProperties();
    if (jobWorkerProperties == null) {
      // The element has no <zeebe:taskDefinition> and therefore won't create a job at runtime
      // (e.g. ScriptTask with <zeebe:script>, BusinessRuleTask with <zeebe:calledDecision>).
      // The presence of JobWorkerProperties is used downstream as a signal that the element
      // produces jobs (see StraightThroughProcessingLoopValidator), so we must not materialise
      // it here.
      return;
    }

    final ExpressionLanguage expressionLanguage = context.getExpressionLanguage();
    final Expression resolved;
    if (priorityDefinition != null) {
      resolved = expressionLanguage.parseExpression(priorityDefinition.getPriority());
    } else {
      final Expression processDefault = context.getCurrentProcess().getDefaultJobPriority();
      resolved =
          processDefault != null
              ? processDefault
              : expressionLanguage.parseExpression(NEUTRAL_PRIORITY);
    }
    jobWorkerProperties.setJobPriority(resolved);
  }
}
