/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class IdLengthValidator implements ModelElementValidator<BaseElement> {

  private final int maxFieldLength;

  public IdLengthValidator(final int maxFieldLength) {
    this.maxFieldLength = maxFieldLength;
  }

  @Override
  public Class<BaseElement> getElementType() {
    return BaseElement.class;
  }

  @Override
  public void validate(
      final BaseElement element, final ValidationResultCollector validationResultCollector) {

    if (element.getId() != null && element.getId().length() > maxFieldLength) {
      validationResultCollector.addError(
          0,
          "IDs must not be longer than the configured max-id-length of "
              + maxFieldLength
              + " characters.");
    }
  }
}
