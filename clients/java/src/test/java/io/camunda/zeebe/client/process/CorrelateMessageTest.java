/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.api.command.InternalClientException;
import io.camunda.zeebe.client.protocol.rest.MessageCorrelationRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CorrelateMessageTest extends ClientRestTest {

  @Test
  void shouldCorrelateMessageWithCorrelationKey() {
    // given
    final String messageName = "name";
    final String correlationKey = "correlationKey";
    final String tenantId = "tenant";
    final Map<String, Object> variables = Collections.singletonMap("foo", "bar");

    // when
    client
        .newCorrelateMessageCommand()
        .messageName(messageName)
        .correlationKey(correlationKey)
        .tenantId(tenantId)
        .variables(variables)
        .send()
        .join();

    // then
    final MessageCorrelationRequest request =
        gatewayService.getLastRequest(MessageCorrelationRequest.class);
    assertThat(request.getName()).isEqualTo(messageName);
    assertThat(request.getCorrelationKey()).isEqualTo(correlationKey);
    assertThat(request.getTenantId()).isEqualTo(tenantId);
    assertThat(request.getVariables()).isEqualTo(variables);
  }

  @Test
  void shouldCorrelateMessageWithoutCorrelationKey() {
    // given
    final String messageName = "name";
    final String tenantId = "tenant";
    final Map<String, Object> variables = Collections.singletonMap("foo", "bar");

    // when
    client
        .newCorrelateMessageCommand()
        .messageName(messageName)
        .withoutCorrelationKey()
        .tenantId(tenantId)
        .variables(variables)
        .send()
        .join();

    // then
    final MessageCorrelationRequest request =
        gatewayService.getLastRequest(MessageCorrelationRequest.class);
    assertThat(request.getName()).isEqualTo(messageName);
    assertThat(request.getCorrelationKey()).isNull();
    assertThat(request.getTenantId()).isEqualTo(tenantId);
    assertThat(request.getVariables()).isEqualTo(variables);
  }

  @Test
  void shouldThrowExceptionWhenVariablesAreNotInMapStructure() {
    // given
    final String messageName = "name";
    final String tenantId = "tenant";
    final String variables = "[]";

    // when then
    assertThatThrownBy(
            () ->
                client
                    .newCorrelateMessageCommand()
                    .messageName(messageName)
                    .withoutCorrelationKey()
                    .tenantId(tenantId)
                    .variables(variables))
        .isInstanceOf(InternalClientException.class)
        .hasMessageContaining(
            String.format("Failed to deserialize json '%s' to 'Map<String, Object>'", variables));
  }
}
