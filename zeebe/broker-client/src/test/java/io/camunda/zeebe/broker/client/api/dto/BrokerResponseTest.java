/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.IllegalBrokerResponseException;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import org.junit.jupiter.api.Test;

final class BrokerResponseTest {

  @Test
  void shouldReturnResponsePayload() {
    // given
    final var response = new BrokerResponse<>("payload", 1, 2);

    // when
    final var payload = response.getResponseOrThrow();

    // then
    assertThat(payload).isEqualTo("payload");
  }

  @Test
  void shouldConvertErrorResponseToException() {
    // given
    final var brokerError = new BrokerError(ErrorCode.INTERNAL_ERROR, "failed");
    final var response = new BrokerErrorResponse<>(brokerError);

    // when/then
    assertThatThrownBy(response::getResponseOrThrow)
        .isInstanceOfSatisfying(
            BrokerErrorException.class,
            error -> assertThat(error.getError()).isEqualTo(brokerError));
  }

  @Test
  void shouldConvertRejectionResponseToException() {
    // given
    final var brokerRejection =
        new BrokerRejection(JobIntent.COMPLETE, 42L, RejectionType.INVALID_ARGUMENT, "invalid");
    final var response = new BrokerRejectionResponse<>(brokerRejection);

    // when/then
    assertThatThrownBy(response::getResponseOrThrow)
        .isInstanceOfSatisfying(
            BrokerRejectionException.class,
            error -> assertThat(error.getRejection()).isEqualTo(brokerRejection));
  }

  @Test
  void shouldRejectUnsupportedBrokerResponse() {
    // given
    final var response = new BrokerResponse<>();

    // when/then
    assertThatThrownBy(response::getResponseOrThrow)
        .isInstanceOf(IllegalBrokerResponseException.class)
        .hasMessageContaining("Expected broker response");
  }
}
