/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import org.junit.jupiter.api.Test;

public final class CorrelationKeyLengthValidatorTest {

  @Test
  void shouldRejectStaticCorrelationKeyIfLengthExceeded() {
    // given
    final var correlationKey = "a".repeat(ProcessValidationUtil.MAX_NAME_FIELD_LENGTH + 1);
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("catch")
            .message(m -> m.name("message").zeebeCorrelationKey(correlationKey))
            .endEvent()
            .done();

    // when / then
    ProcessValidationUtil.validateProcess(
        process,
        ExpectedValidationResult.expect(
            ZeebeSubscription.class,
            "Correlation keys must not be longer than the configured max-name-length of %s characters."
                .formatted(ProcessValidationUtil.MAX_NAME_FIELD_LENGTH)));
  }

  @Test
  void shouldAllowDynamicCorrelationKey() {
    // given
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .receiveTask("catch")
            .message(m -> m.name("message").zeebeCorrelationKeyExpression("=correlationKey"))
            .endEvent()
            .done();

    // when / then
    ProcessValidationUtil.validateProcess(process);
  }
}
