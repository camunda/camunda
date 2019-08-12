/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.validation;

import io.zeebe.model.bpmn.instance.zeebe.ZeebeLoopCharacteristics;
import java.util.Optional;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeLoopCharacteristicsValidator
    implements ModelElementValidator<ZeebeLoopCharacteristics> {

  private final ZeebeExpressionValidator expressionValidator;

  public ZeebeLoopCharacteristicsValidator(final ZeebeExpressionValidator expressionValidator) {
    this.expressionValidator = expressionValidator;
  }

  @Override
  public Class<ZeebeLoopCharacteristics> getElementType() {
    return ZeebeLoopCharacteristics.class;
  }

  @Override
  public void validate(
      final ZeebeLoopCharacteristics element,
      final ValidationResultCollector validationResultCollector) {
    expressionValidator.validateJsonPath(element.getInputCollection(), validationResultCollector);

    Optional.ofNullable(element.getOutputElement())
        .filter(e -> !e.isEmpty())
        .ifPresent(e -> expressionValidator.validateJsonPath(e, validationResultCollector));
  }
}
