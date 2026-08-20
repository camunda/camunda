/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeFormDefinitionDeploymentBindingValidator
    implements ModelElementValidator<ZeebeFormDefinition> {

  private final List<FormMetadataValue> formMetadata;

  public ZeebeFormDefinitionDeploymentBindingValidator(final DeploymentRecord deployment) {
    formMetadata = deployment.getFormMetadata();
  }

  @Override
  public Class<ZeebeFormDefinition> getElementType() {
    return ZeebeFormDefinition.class;
  }

  @Override
  public void validate(
      final ZeebeFormDefinition formDefinition,
      final ValidationResultCollector validationResultCollector) {
    if (formMetadata.stream()
        .noneMatch(form -> formDefinition.getFormId().equals(form.getFormId()))) {
      validationResultCollector.addError(
          0,
          "Expected to find form with id '%s' in current deployment, but not found."
              .formatted(formDefinition.getFormId()));
    }
  }
}
