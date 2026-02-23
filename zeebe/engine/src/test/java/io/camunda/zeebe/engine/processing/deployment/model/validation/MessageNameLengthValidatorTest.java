/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.Message;
import org.junit.jupiter.api.Test;

public final class MessageNameLengthValidatorTest {

  @Test
  void shouldRejectStaticMessageNameIfLengthExceeded() {
    // given
    final var messageName = "a".repeat(ProcessValidationUtil.MAX_NAME_FIELD_LENGTH + 1);
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("catch")
            .message(m -> m.name(messageName).zeebeCorrelationKeyExpression("correlationKey"))
            .endEvent()
            .done();

    // when / then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            Message.class,
            "Message names must not be longer than the configured max-name-length of %s characters."
                .formatted(ProcessValidationUtil.MAX_NAME_FIELD_LENGTH)));
  }

  @Test
  void shouldAllowStaticMessageNameAtMaxLength() {
    // given
    final var messageName = "a".repeat(ProcessValidationUtil.MAX_NAME_FIELD_LENGTH);
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("catch")
            .message(m -> m.name(messageName).zeebeCorrelationKeyExpression("correlationKey"))
            .endEvent()
            .done();

    // when / then
    ProcessValidationUtil.validateProcess(process);
  }

  @Test
  void shouldAllowDynamicMessageName() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("catch")
            .message(m -> m.name("=messageName").zeebeCorrelationKeyExpression("correlationKey"))
            .endEvent()
            .done();

    // when / then
    ProcessValidationUtil.validateProcess(process);
  }
}
