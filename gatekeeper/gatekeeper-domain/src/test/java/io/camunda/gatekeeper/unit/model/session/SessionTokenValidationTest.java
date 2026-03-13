/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.unit.model.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gatekeeper.model.session.SessionTokenValidation;
import org.junit.jupiter.api.Test;

final class SessionTokenValidationTest {

  @Test
  void successFactoryCreatesValidResult() {
    final var result = SessionTokenValidation.success("alice", "session-123");

    assertThat(result.valid()).isTrue();
    assertThat(result.subject()).isEqualTo("alice");
    assertThat(result.sessionId()).isEqualTo("session-123");
    assertThat(result.reason()).isEmpty();
  }

  @Test
  void failureFactoryCreatesInvalidResult() {
    final var result = SessionTokenValidation.failure("token expired");

    assertThat(result.valid()).isFalse();
    assertThat(result.subject()).isNull();
    assertThat(result.sessionId()).isNull();
    assertThat(result.reason()).isEqualTo("token expired");
  }
}
