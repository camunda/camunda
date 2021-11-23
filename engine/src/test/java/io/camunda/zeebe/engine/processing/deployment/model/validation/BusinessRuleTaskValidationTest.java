/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.BusinessRuleTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledDecision;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class BusinessRuleTaskValidationTest {

  @Test
  void emptyDecisionIdExpression() {
    // when
    final var process =
        process(task -> task.zeebeCalledDecisionIdExpression("").zeebeResultVariable("result"));

    // then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            ZeebeCalledDecision.class, "Attribute 'decisionId' must be present and not empty"));
  }

  @Test
  void invalidDecisionIdExpression() {
    // when
    final var process = process(task -> task.zeebeCalledDecisionIdExpression("invalid id"));

    // then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            ZeebeCalledDecision.class, "failed to parse expression 'invalid id'"));
  }

  private BpmnModelInstance process(final Consumer<BusinessRuleTaskBuilder> taskBuilder) {
    return Bpmn.createExecutableProcess("process")
        .startEvent()
        .businessRuleTask("task", taskBuilder)
        .done();
  }
}
