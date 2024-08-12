/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.service.MessageServices;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
import io.camunda.zeebe.gateway.protocol.rest.MessageCorrelationResponse;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(MessageController.class)
public class MessageControllerTest extends RestControllerTest {

  private static final String MESSAGE_BASE_URL = "/v2/messages";
  private static final String CORRELATION_ENDPOINT = MESSAGE_BASE_URL + "/correlation";

  @MockBean MessageServices<MessageCorrelationResponse> messageServices;
  @MockBean MultiTenancyCfg multiTenancyCfg;
  @Captor ArgumentCaptor<MessageServices.CorrelateMessageRequest> requestCaptor;

  @BeforeEach
  void setup() {
    when(messageServices.withAuthentication(any(Authentication.class))).thenReturn(messageServices);
  }

  @Test
  void shouldCorrelateMessage() {
    // given
    when(multiTenancyCfg.isEnabled()).thenReturn(false);
    when(messageServices.correlateMessage(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new MessageCorrelationRecord()
                    .setMessageKey(123L)
                    .setTenantId("<default>")
                    .setProcessInstanceKey(321L)));

    final var request =
        """
        {
          "name": "messageName",
          "correlationKey": "correlationKey",
          "variables": {
            "key": "value"
          },
          "tenantId": "<default>"
        }""";

    // when then
    final var response =
        webClient
            .post()
            .uri(CORRELATION_ENDPOINT)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk();

    Mockito.verify(messageServices).correlateMessage(requestCaptor.capture());
    final var capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("messageName");
    assertThat(capturedRequest.correlationKey()).isEqualTo("correlationKey");
    assertThat(capturedRequest.variables()).containsExactly(Map.entry("key", "value"));
    assertThat(capturedRequest.tenantId()).isEqualTo("<default>");

    response
        .expectBody()
        .json(
            """
        {
          "key": 123,
          "tenantId": "<default>",
          "processInstanceKey": 321
        }""");
  }

  @Test
  void shouldRejectMessageCorrelationWithoutMessageName() {
    // given
    final var request =
        """
        {
          "correlationKey": "correlationKey",
          "variables": {
            "key": "value"
          }
        }""";

    // when then
    webClient
        .post()
        .uri(CORRELATION_ENDPOINT)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No messageName provided.",
              "instance": "%s"
            }"""
                .formatted(CORRELATION_ENDPOINT));
    verifyNoInteractions(messageServices);
  }

  @Test
  void shouldRejectMessageCorrelationWithEmptyMessageName() {
    // given
    final var request =
        """
        {
          "name": "",
          "correlationKey": "correlationKey",
          "variables": {
            "key": "value"
          }
        }""";

    // when then
    webClient
        .post()
        .uri(CORRELATION_ENDPOINT)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No messageName provided.",
              "instance": "%s"
            }"""
                .formatted(CORRELATION_ENDPOINT));
    verifyNoInteractions(messageServices);
  }
}
