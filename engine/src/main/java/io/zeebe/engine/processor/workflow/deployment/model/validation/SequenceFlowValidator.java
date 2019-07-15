/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.validation;

import io.zeebe.model.bpmn.instance.ConditionExpression;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class SequenceFlowValidator implements ModelElementValidator<ConditionExpression> {

  private final ZeebeExpressionValidator expressionValidator;

  public SequenceFlowValidator(ZeebeExpressionValidator expressionValidator) {
    this.expressionValidator = expressionValidator;
  }

  @Override
  public Class<ConditionExpression> getElementType() {
    return ConditionExpression.class;
  }

  @Override
  public void validate(
      ConditionExpression element, ValidationResultCollector validationResultCollector) {
    expressionValidator.validateExpression(element.getTextContent(), validationResultCollector);
  }
}
