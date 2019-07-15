/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.validation;

import io.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeSubscriptionValidator implements ModelElementValidator<ZeebeSubscription> {

  private final ZeebeExpressionValidator expressionValidator;

  public ZeebeSubscriptionValidator(ZeebeExpressionValidator expressionValidator) {
    this.expressionValidator = expressionValidator;
  }

  @Override
  public Class<ZeebeSubscription> getElementType() {
    return ZeebeSubscription.class;
  }

  @Override
  public void validate(
      ZeebeSubscription element, ValidationResultCollector validationResultCollector) {
    expressionValidator.validateJsonPath(element.getCorrelationKey(), validationResultCollector);
  }
}
