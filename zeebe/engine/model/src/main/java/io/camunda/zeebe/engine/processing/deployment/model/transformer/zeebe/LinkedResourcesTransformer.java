/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableJobWorkerElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.JobWorkerProperties;
import io.camunda.zeebe.engine.processing.deployment.model.element.LinkedResource;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeLinkedResource;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeLinkedResources;
import java.util.List;
import java.util.Optional;

public class LinkedResourcesTransformer {

  public <T extends FlowElement> void transform(
      final ExecutableJobWorkerElement jobWorkerElement,
      final ZeebeLinkedResources linkedResources) {
    if (linkedResources == null) {
      return;
    }

    final var jobWorkerProperties =
        Optional.ofNullable(jobWorkerElement.getJobWorkerProperties())
            .orElse(new JobWorkerProperties());
    jobWorkerElement.setJobWorkerProperties(jobWorkerProperties);

    final List<LinkedResource> collected =
        linkedResources.getLinkedResources().stream().map(this::toLinkedResourceModel).toList();
    jobWorkerProperties.setLinkedResources(collected);
  }

  private LinkedResource toLinkedResourceModel(final ZeebeLinkedResource zeebeLinkedResource) {
    final LinkedResource linkedResource = new LinkedResource();
    linkedResource.setResourceId(zeebeLinkedResource.getResourceId());
    linkedResource.setLinkName(zeebeLinkedResource.getLinkName());
    linkedResource.setResourceType(zeebeLinkedResource.getResourceType());
    linkedResource.setBindingType(zeebeLinkedResource.getBindingType());
    linkedResource.setVersionTag(zeebeLinkedResource.getVersionTag());
    return linkedResource;
  }
}
