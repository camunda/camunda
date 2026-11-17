/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeLinkedResource;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.ResourceMetadataValue;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeLinkedResourceDeploymentBindingValidator
    implements ModelElementValidator<ZeebeLinkedResource> {

  private final List<ResourceMetadataValue> resourceMetadata;
  private final List<FormMetadataValue> formMetadata;

  public ZeebeLinkedResourceDeploymentBindingValidator(final DeploymentRecord deployment) {
    resourceMetadata = deployment.getResourceMetadata();
    formMetadata = deployment.getFormMetadata();
  }

  @Override
  public Class<ZeebeLinkedResource> getElementType() {
    return ZeebeLinkedResource.class;
  }

  @Override
  public void validate(
      final ZeebeLinkedResource linkedResource,
      final ValidationResultCollector validationResultCollector) {
    final var resourceId = linkedResource.getResourceId();
    if (resourceId == null || resourceId.isBlank()) {
      return;
    }

    final var resourceType = linkedResource.getResourceType();
    final var isFormResource = "form".equalsIgnoreCase(resourceType);
    if (isFormResource) {
      if (formMetadata.stream().noneMatch(r -> resourceId.equals(r.getFormId()))) {
        validationResultCollector.addError(
            0,
            "Expected to find form with id '%s' in current deployment, but not found."
                .formatted(resourceId));
      }
    } else if (resourceMetadata.stream().noneMatch(r -> resourceId.equals(r.getResourceId()))) {
      validationResultCollector.addError(
          0,
          "Expected to find resource with id '%s' in current deployment, but not found."
              .formatted(resourceId));
    }
  }
}
