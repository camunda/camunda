/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.instance.NamedBpmnElement;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class NameLengthValidator<T extends NamedBpmnElement> implements ModelElementValidator<T> {

  private final Class<T> elementClass;
  private final int maxFieldLength;

  public NameLengthValidator(final Class<T> elementClass, final int maxFieldLength) {
    this.elementClass = elementClass;
    this.maxFieldLength = maxFieldLength;
  }

  @Override
  public Class<T> getElementType() {
    return elementClass;
  }

  @Override
  public void validate(final T element, final ValidationResultCollector validationResultCollector) {

    if (element.getName() != null && element.getName().length() > maxFieldLength) {
      validationResultCollector.addError(
          0,
          "Names must not be longer than the configured max-name-length of "
              + maxFieldLength
              + " characters.");
    }
  }
}
