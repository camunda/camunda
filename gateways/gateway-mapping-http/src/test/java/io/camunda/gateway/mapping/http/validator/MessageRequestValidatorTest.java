/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.validator;

import static io.camunda.gateway.mapping.http.validator.MessageRequestValidator.validateMessageCorrelationRequest;
import static io.camunda.gateway.mapping.http.validator.MessageRequestValidator.validateMessagePublicationRequest;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.MessageCorrelationRequest;
import io.camunda.gateway.protocol.model.MessagePublicationRequest;
import org.junit.jupiter.api.Test;

class MessageRequestValidatorTest {

  private static final int MAX_NAME_FIELD_LENGTH = 123;

  @Test
  void shouldRejectCorrelationRequestWhenCorrelationKeyExceedsMaxLength() {
    // given
    final var request =
        new MessageCorrelationRequest()
            .name("message-name")
            .correlationKey("a".repeat(MAX_NAME_FIELD_LENGTH + 1));

    // when
    final var validationResult = validateMessageCorrelationRequest(request, MAX_NAME_FIELD_LENGTH);

    // then
    assertThat(validationResult).isPresent();
    assertThat(validationResult.get().getDetail())
        .contains("correlationKey")
        .contains(String.valueOf(MAX_NAME_FIELD_LENGTH));
  }

  @Test
  void shouldRejectPublicationRequestWhenCorrelationKeyExceedsMaxLength() {
    // given
    final var request =
        new MessagePublicationRequest()
            .name("message-name")
            .correlationKey("a".repeat(MAX_NAME_FIELD_LENGTH + 1));

    // when
    final var validationResult = validateMessagePublicationRequest(request, MAX_NAME_FIELD_LENGTH);

    // then
    assertThat(validationResult).isPresent();
    assertThat(validationResult.get().getDetail())
        .contains("correlationKey")
        .contains(String.valueOf(MAX_NAME_FIELD_LENGTH));
  }

  @Test
  void shouldAcceptCorrelationRequestWhenCorrelationKeyAtMaxLength() {
    // given
    final var request =
        new MessageCorrelationRequest()
            .name("message-name")
            .correlationKey("a".repeat(MAX_NAME_FIELD_LENGTH));

    // when
    final var validationResult = validateMessageCorrelationRequest(request, MAX_NAME_FIELD_LENGTH);

    // then
    assertThat(validationResult).isEmpty();
  }

  @Test
  void shouldAcceptPublicationRequestWhenCorrelationKeyAtMaxLength() {
    // given
    final var request =
        new MessagePublicationRequest()
            .name("message-name")
            .correlationKey("a".repeat(MAX_NAME_FIELD_LENGTH));

    // when
    final var validationResult = validateMessagePublicationRequest(request, MAX_NAME_FIELD_LENGTH);

    // then
    assertThat(validationResult).isEmpty();
  }
}
