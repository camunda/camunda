/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import static io.camunda.zeebe.engine.processing.deployment.model.validation.ExpectedValidationResult.expect;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.CallActivityBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledElement;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class CallActivityBusinessIdValidationTest {

  private static final String INVALID_FEEL_EXPRESSION = "=a & b";
  private static final String INVALID_FEEL_EXPRESSION_MESSAGE =
      "failed to parse expression 'a & b'";

  private static BpmnModelInstance processWithCallActivity(
      final Consumer<CallActivityBuilder> modifier) {
    return Bpmn.createExecutableProcess("process")
        .startEvent()
        .callActivity("call", modifier)
        .done();
  }

  @Test
  void shouldRejectInvalidBusinessIdFeelExpression() {
    // given
    final var process =
        processWithCallActivity(
            c -> c.zeebeProcessId("child").zeebeBusinessId(INVALID_FEEL_EXPRESSION));

    // when / then
    ProcessValidationUtil.validateProcess(
        process, expect(ZeebeCalledElement.class, INVALID_FEEL_EXPRESSION_MESSAGE));
  }

  @Test
  void shouldAcceptValidBusinessIdFeelExpression() {
    // given
    final var process =
        processWithCallActivity(c -> c.zeebeProcessId("child").zeebeBusinessId("=orderId"));

    // when / then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  void shouldAcceptLiteralBusinessId() {
    // given
    final var process =
        processWithCallActivity(c -> c.zeebeProcessId("child").zeebeBusinessId("order-123"));

    // when / then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  void shouldAcceptEmptyBusinessId() {
    // given
    final var process = processWithCallActivity(c -> c.zeebeProcessId("child").zeebeBusinessId(""));

    // when / then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  void shouldAcceptAbsentBusinessId() {
    // given
    final var process = processWithCallActivity(c -> c.zeebeProcessId("child"));

    // when / then
    ProcessValidationUtil.validateProcess(process);
  }
}
