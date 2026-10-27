/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.AgentElementTypeTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.JobPriorityDefinitionTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.LinkedResourcesTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.TaskDefinitionTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.TaskHeadersTransformer;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAgentDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeJobPriorityDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeLinkedResources;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;

/**
 * Version 2 of the {@code SERVICE_TASK_JOB_WORKER} handler (see {@link
 * io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer}). {@link
 * JobWorkerElementTransformer} stays frozen at v1 forever.
 */
public final class JobWorkerElementTransformerV2<T extends FlowElement>
    implements ModelElementTransformer<T> {

  private final Class<T> type;
  private final TaskDefinitionTransformer taskDefinitionTransformer =
      new TaskDefinitionTransformer();
  private final TaskHeadersTransformer taskHeadersTransformer = new TaskHeadersTransformer();
  private final JobPriorityDefinitionTransformer jobPriorityDefinitionTransformer =
      new JobPriorityDefinitionTransformer();
  private final LinkedResourcesTransformer linkedResourcesTransformer =
      new LinkedResourcesTransformer();
  private final AgentElementTypeTransformer agentElementTypeTransformer =
      new AgentElementTypeTransformer();

  public JobWorkerElementTransformerV2(final Class<T> type) {
    this.type = type;
  }

  @Override
  public Class<T> getType() {
    return type;
  }

  @Override
  public void transform(final T element, final TransformContext context) {

    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableJobWorkerElement jobWorkerElement =
        process.getElementById(element.getId(), ExecutableJobWorkerElement.class);

    final var taskDefinition = element.getSingleExtensionElement(ZeebeTaskDefinition.class);
    taskDefinitionTransformer.transform(jobWorkerElement, context, taskDefinition);

    final var taskHeaders = element.getSingleExtensionElement(ZeebeTaskHeaders.class);
    taskHeadersTransformer.transform(jobWorkerElement, taskHeaders, element);

    final var jobPriorityDefinition =
        element.getSingleExtensionElement(ZeebeJobPriorityDefinition.class);
    jobPriorityDefinitionTransformer.transform(jobWorkerElement, context, jobPriorityDefinition);

    if (type.equals(ServiceTask.class)) {
      final var linkedResources = element.getSingleExtensionElement(ZeebeLinkedResources.class);
      linkedResourcesTransformer.transform(jobWorkerElement, linkedResources);

      final var agentDefinition = element.getSingleExtensionElement(ZeebeAgentDefinition.class);
      agentElementTypeTransformer.transform(jobWorkerElement, agentDefinition);
      if (jobWorkerElement.isAgentDefinition()) {
        process.markAgentic();
      }
    }
  }
}
