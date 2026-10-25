/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe.ConsentDefinitionTransformer;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeConsent;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeRelationship;

/**
 * Version 3 of the {@code SERVICE_TASK_JOB_WORKER} handler: version 2, plus the consent definition
 * of a task that declares {@code zeebe:consent} or {@code zeebe:relationship}.
 */
public final class JobWorkerElementTransformerV3<T extends FlowElement>
    implements ModelElementTransformer<T> {

  private final JobWorkerElementTransformerV2<T> v2;
  private final ConsentDefinitionTransformer consentDefinitionTransformer =
      new ConsentDefinitionTransformer();

  public JobWorkerElementTransformerV3(final Class<T> type) {
    v2 = new JobWorkerElementTransformerV2<>(type);
  }

  @Override
  public Class<T> getType() {
    return v2.getType();
  }

  @Override
  public void transform(final T element, final TransformContext context) {
    v2.transform(element, context);

    final ExecutableJobWorkerElement jobWorkerElement =
        context
            .getCurrentProcess()
            .getElementById(element.getId(), ExecutableJobWorkerElement.class);
    consentDefinitionTransformer.transform(
        jobWorkerElement,
        context,
        element.getSingleExtensionElement(ZeebeConsent.class),
        element.getSingleExtensionElement(ZeebeRelationship.class));
  }
}
