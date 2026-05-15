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
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeJobPriorityDefinition;

public final class JobPriorityDefinitionTransformer {

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

    final Expression resolved;
    if (priorityDefinition != null) {
      final String raw = priorityDefinition.getPriority();
      // Task-level literal "0" overrides any process-level default, but the effective priority
      // is still 0, which is exactly what BpmnJobBehavior.evalPriorityExp returns for a null
      // expression. Leaving jobPriority unset skips a FEEL evaluation per job creation.
      if (ZeebeJobPriorityDefinition.DEFAULT_LITERAL_PRIORITY.equals(raw)) {
        return;
      }
      resolved = context.getExpressionLanguage().parseExpression(raw);
    } else {
      // Falls through to BpmnJobBehavior.evalPriorityExp, which returns 0 when jobPriority is
      // null. Leaving the field unset avoids an unnecessary FEEL evaluation per job creation.
      resolved = context.getCurrentProcess().getDefaultJobPriority();
    }
    if (resolved != null) {
      jobWorkerProperties.setJobPriority(resolved);
    }
  }
}
