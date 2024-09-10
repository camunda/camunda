/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.camunda.service.SignalServices;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(SignalController.class)
public class SignalControllerTest extends RestControllerTest {

  private static final String SIGNALS_BASE_URL = "/v2/signals";
  private static final String BROADCAST_SIGNAL_ENDPOINT = SIGNALS_BASE_URL + "/broadcast";
  private static final String EXPECTED_PUBLICATION_RESPONSE =
      """
       {
         "key": 123,
         "tenantId": "<default>"
       }""";

  @MockBean SignalServices signalServices;

  @BeforeEach
  void setup() {
    when(signalServices.withAuthentication(any(Authentication.class))).thenReturn(signalServices);
  }

  @Test
  void shouldBroadcastSignal() {
    // given
    when(signalServices.broadcastSignal(anyString(), anyMap(), anyString()))
        .thenReturn(buildSignalResponse());

    final var request =
        """
        {
          "signalName": "signalName",
          "variables": {
            "key": "value"
          },
          "tenantId": "<default>"
        }""";

    // when then
    webClient
        .post()
        .uri(BROADCAST_SIGNAL_ENDPOINT)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_PUBLICATION_RESPONSE);

    Mockito.verify(signalServices)
        .broadcastSignal("signalName", Map.of("key", "value"), "<default>");
  }

  @Test
  void shouldBroadcastSignalWithEmptySignalName() {
    // given
    when(signalServices.broadcastSignal(anyString(), anyMap(), anyString()))
        .thenReturn(buildSignalResponse());

    final var request =
        """
        {
          "signalName": "",
          "variables": {
            "key": "value"
          },
          "tenantId": "<default>"
        }""";

    // when then
    webClient
        .post()
        .uri(BROADCAST_SIGNAL_ENDPOINT)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_PUBLICATION_RESPONSE);

    Mockito.verify(signalServices).broadcastSignal("", Map.of("key", "value"), "<default>");
  }

  @Test
  void shouldRejectBroadcastSignalWithoutSignalName() {
    // given
    final var request =
        """
        {
          "variables": {
            "key": "value"
          },
          "tenantId": "<default>"
        }""";
    final var expectedBody =
        """
        {
            "type":"about:blank",
            "title":"INVALID_ARGUMENT",
            "status":400,
            "detail":"No signalName provided.",
            "instance":"/v2/signals/broadcast"
         }""";

    // when then
    webClient
        .post()
        .uri(BROADCAST_SIGNAL_ENDPOINT)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(expectedBody);
  }

  private CompletableFuture<BrokerResponse<SignalRecord>> buildSignalResponse() {
    final var record =
        new SignalRecord()
            .setSignalName("signalName")
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    return CompletableFuture.completedFuture(new BrokerResponse<>(record, 1, 123));
  }
}
