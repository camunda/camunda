/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class WorkerTypeLengthValidator implements ModelElementValidator<ZeebeTaskDefinition> {

  private final int maxTypeLength;

  public WorkerTypeLengthValidator(final int maxTypeLength) {
    this.maxTypeLength = maxTypeLength;
  }

  @Override
  public Class<ZeebeTaskDefinition> getElementType() {
    return ZeebeTaskDefinition.class;
  }

  @Override
  public void validate(
      final ZeebeTaskDefinition element,
      final ValidationResultCollector validationResultCollector) {
    if (element.getType() != null && element.getType().length() > maxTypeLength) {
      validationResultCollector.addError(
          0,
          "Worker types must not be longer than the configured max-worker-type-length of "
              + maxTypeLength
              + " characters.");
    }
  }
}
