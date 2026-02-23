/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public final class CorrelationKeyLengthValidator
    implements ModelElementValidator<ZeebeSubscription> {

  private final int maxNameFieldLength;

  public CorrelationKeyLengthValidator(final int maxNameFieldLength) {
    this.maxNameFieldLength = maxNameFieldLength;
  }

  @Override
  public Class<ZeebeSubscription> getElementType() {
    return ZeebeSubscription.class;
  }

  @Override
  public void validate(
      final ZeebeSubscription element, final ValidationResultCollector validationResultCollector) {
    final String correlationKeyExpression = element.getCorrelationKey();
    if (correlationKeyExpression == null) {
      return;
    }

    if (correlationKeyExpression.length() > maxNameFieldLength) {
      validationResultCollector.addError(
          0,
          "Correlation keys must not be longer than the configured max-name-length of "
              + maxNameFieldLength
              + " characters.");
    }
  }
}
