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

    if (isNotBackedByJob(element)) {
      return;
    }

    final Expression resolved;
    if (priorityDefinition != null) {
      final String raw = priorityDefinition.getPriority();
      // Literal "0" is equivalent to the null -> 0 fast path in BpmnJobBehavior.evalPriorityExp.
      if (ZeebeJobPriorityDefinition.DEFAULT_LITERAL_PRIORITY.equals(raw)) {
        return;
      }
      resolved = context.getExpressionLanguage().parseExpression(raw);
    } else {
      resolved = context.getCurrentProcess().getDefaultJobPriority();
    }
    if (resolved != null) {
      element.getJobWorkerProperties().setJobPriority(resolved);
    }
  }

  private static boolean isNotBackedByJob(final ExecutableJobWorkerElement element) {
    return element.getJobWorkerProperties() == null;
  }
}
