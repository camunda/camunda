/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;

final class BpmnJobActivationBehaviorTest {

  private static final long JOB_KEY = 42L;

  @Test
  void shouldFallBackToCauseNeutralMessageWhenFailureIsNotAMismatch() {
    // given - an injection failure the path cannot attribute to a specific reference, e.g. the
    // job's variables are not valid msgpack, which surfaces as an IOException rather than a
    // SecretPointerMismatchException
    final var failure = new IOException("variables document quoting a secret value");

    // when
    final var message = BpmnJobActivationBehavior.secretInjectionIncidentMessage(JOB_KEY, failure);

    // then - the incident carries the cause-neutral fallback, naming neither a reference nor the
    // exception's own message, which may quote the variables document and with it secret data
    assertThat(message)
        .isEqualTo(BpmnIncidentBehavior.SECRET_INJECTION_FAILED_MESSAGE.formatted(JOB_KEY))
        .doesNotContain(failure.getMessage());
  }
}
