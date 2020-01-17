/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.validation;

import io.zeebe.model.bpmn.instance.ConditionExpression;
import io.zeebe.msgpack.el.CompiledJsonCondition;
import io.zeebe.msgpack.el.JsonConditionFactory;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public final class SequenceFlowValidator implements ModelElementValidator<ConditionExpression> {

  @Override
  public Class<ConditionExpression> getElementType() {
    return ConditionExpression.class;
  }

  @Override
  public void validate(
      final ConditionExpression element,
      final ValidationResultCollector validationResultCollector) {

    final String expression = element.getTextContent();
    final CompiledJsonCondition condition = JsonConditionFactory.createCondition(expression);

    if (!condition.isValid()) {
      validationResultCollector.addError(
          0, String.format("Condition expression is invalid: %s", condition.getErrorMessage()));
    }
  }
}
