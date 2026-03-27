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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.SignalServices;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.controller.adapter.DefaultSignalServiceAdapter;
import io.camunda.zeebe.gateway.rest.controller.generated.GeneratedSignalController;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@Import(DefaultSignalServiceAdapter.class)
@WebMvcTest(GeneratedSignalController.class)
public class SignalControllerTest extends RestControllerTest {

  private static final String SIGNALS_BASE_URL = "/v2/signals";
  private static final String BROADCAST_SIGNAL_ENDPOINT = SIGNALS_BASE_URL + "/broadcast";
  private static final String EXPECTED_PUBLICATION_RESPONSE =
      """
          {
            "signalKey": "123",
            "tenantId": "tenantId"
          }""";

  @MockitoBean MultiTenancyConfiguration multiTenancyCfg;
  @MockitoBean SignalServices signalServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setup() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldBroadcastSignal() {
    // given
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_NON_DEFAULT_TENANT);
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    when(signalServices.broadcastSignal(anyString(), anyMap(), anyString(), any()))
        .thenReturn(buildSignalResponse("tenantId"));

    final var request =
        """
            {
              "signalName": "signalName",
              "variables": {
                "key": "value"
              },
              "tenantId": "tenantId"
            }""";

    // when then
    final ResponseSpec response =
        webClient
            .post()
            .uri(BROADCAST_SIGNAL_ENDPOINT)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk();

    response.expectBody().json(EXPECTED_PUBLICATION_RESPONSE, JsonCompareMode.STRICT);
    Mockito.verify(signalServices)
        .broadcastSignal(eq("signalName"), eq(Map.of("key", "value")), eq("tenantId"), any());
  }

  @Test
  void shouldBroadcastSignalWithMultitenancyDisabled() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(false);
    when(signalServices.broadcastSignal(anyString(), anyMap(), anyString(), any()))
        .thenReturn(buildSignalResponse(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

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
        .isOk();

    Mockito.verify(signalServices)
        .broadcastSignal(
            eq("signalName"),
            eq(Map.of("key", "value")),
            eq(TenantOwned.DEFAULT_TENANT_IDENTIFIER),
            any());
  }

  @Test
  void shouldBroadcastSignalWithEmptySignalName() {
    // given
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_NON_DEFAULT_TENANT);
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    when(signalServices.broadcastSignal(anyString(), anyMap(), anyString(), any()))
        .thenReturn(buildSignalResponse("tenantId"));

    final var request =
        """
            {
              "signalName": "",
              "variables": {
                "key": "value"
              },
              "tenantId": "tenantId"
            }""";

    // when then
    final ResponseSpec response =
        webClient
            .post()
            .uri(BROADCAST_SIGNAL_ENDPOINT)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk();

    response.expectBody().json(EXPECTED_PUBLICATION_RESPONSE, JsonCompareMode.STRICT);
    Mockito.verify(signalServices)
        .broadcastSignal(eq(""), eq(Map.of("key", "value")), eq("tenantId"), any());
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
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  private CompletableFuture<BrokerResponse<SignalRecord>> buildSignalResponse(
      final String tenantId) {
    final var record = new SignalRecord().setSignalName("signalName").setTenantId(tenantId);
    return CompletableFuture.completedFuture(new BrokerResponse<>(record, 1, 123));
  }
}
