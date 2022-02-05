/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.TaskDefinitionTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.TaskHeadersTransformer;
import io.camunda.zeebe.model.bpmn.instance.BusinessRuleTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;

public final class BusinessRuleTaskTransformer
    implements ModelElementTransformer<BusinessRuleTask> {

  private final TaskDefinitionTransformer taskDefinitionTransformer =
      new TaskDefinitionTransformer();
  private final TaskHeadersTransformer taskHeadersTransformer = new TaskHeadersTransformer();

  @Override
  public Class<BusinessRuleTask> getType() {
    return BusinessRuleTask.class;
  }

  @Override
  public void transform(final BusinessRuleTask element, final TransformContext context) {

    final ExecutableProcess process = context.getCurrentProcess();
    final var executableTask =
        process.getElementById(element.getId(), ExecutableJobWorkerElement.class);

    final var taskDefinition = element.getSingleExtensionElement(ZeebeTaskDefinition.class);
    taskDefinitionTransformer.transform(executableTask, context, taskDefinition);

    final var taskHeaders = element.getSingleExtensionElement(ZeebeTaskHeaders.class);
    taskHeadersTransformer.transform(executableTask, taskHeaders, element);
  }
}
