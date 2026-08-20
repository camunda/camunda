/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import java.util.List;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeCalledElementDeploymentBindingValidator
    implements ModelElementValidator<ZeebeCalledElement> {

  private final List<ProcessMetadataValue> processesMetadata;

  public ZeebeCalledElementDeploymentBindingValidator(final DeploymentRecord deployment) {
    processesMetadata = deployment.getProcessesMetadata();
  }

  @Override
  public Class<ZeebeCalledElement> getElementType() {
    return ZeebeCalledElement.class;
  }

  @Override
  public void validate(
      final ZeebeCalledElement calledElement,
      final ValidationResultCollector validationResultCollector) {
    if (processesMetadata.stream()
        .noneMatch(process -> calledElement.getProcessId().equals(process.getBpmnProcessId()))) {
      validationResultCollector.addError(
          0,
          "Expected to find process with id '%s' in current deployment, but not found."
              .formatted(calledElement.getProcessId()));
    }
  }
}
