/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableScriptTask;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.JobPriorityDefinitionTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.LinkedResourcesTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.ScriptTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.TaskDefinitionTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.TaskHeadersTransformer;
import io.camunda.zeebe.model.bpmn.instance.ScriptTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeJobPriorityDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeLinkedResources;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeScript;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;

/**
 * Version 2 of the {@code SCRIPT_TASK} handler (see {@link
 * io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer}). Adds
 * support for {@code zeebe:linkedResources} on job worker script tasks. {@link
 * ScriptTaskTransformer} stays frozen at v1 forever.
 */
public final class ScriptTaskTransformerV2 implements ModelElementTransformer<ScriptTask> {

  private final TaskDefinitionTransformer taskDefinitionTransformer =
      new TaskDefinitionTransformer();
  private final TaskHeadersTransformer taskHeadersTransformer = new TaskHeadersTransformer();
  private final JobPriorityDefinitionTransformer jobPriorityDefinitionTransformer =
      new JobPriorityDefinitionTransformer();
  private final ScriptTransformer scriptTransformer = new ScriptTransformer();
  private final LinkedResourcesTransformer linkedResourcesTransformer =
      new LinkedResourcesTransformer();

  @Override
  public Class<ScriptTask> getType() {
    return ScriptTask.class;
  }

  @Override
  public void transform(final ScriptTask element, final TransformContext context) {
    final ExecutableProcess process = context.getCurrentProcess();
    final var executableTask = process.getElementById(element.getId(), ExecutableScriptTask.class);

    final var taskDefinition = element.getSingleExtensionElement(ZeebeTaskDefinition.class);
    taskDefinitionTransformer.transform(executableTask, context, taskDefinition);

    final var taskHeaders = element.getSingleExtensionElement(ZeebeTaskHeaders.class);
    taskHeadersTransformer.transform(executableTask, taskHeaders, element);

    final var jobPriorityDefinition =
        element.getSingleExtensionElement(ZeebeJobPriorityDefinition.class);
    jobPriorityDefinitionTransformer.transform(executableTask, context, jobPriorityDefinition);

    final var zeebeScript = element.getSingleExtensionElement(ZeebeScript.class);
    scriptTransformer.transform(executableTask, context, zeebeScript);

    final var linkedResources = element.getSingleExtensionElement(ZeebeLinkedResources.class);
    linkedResourcesTransformer.transform(executableTask, linkedResources);
  }
}
