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

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.MessageServices;
import io.camunda.service.MessageServices.CorrelateMessageRequest;
import io.camunda.service.MessageServices.PublicationMessageRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(MessageController.class)
public class MessageControllerTest extends RestControllerTest {

  private static final String MESSAGE_BASE_URL = "/v2/messages";
  private static final String CORRELATION_ENDPOINT = MESSAGE_BASE_URL + "/correlation";
  private static final String PUBLICATION_ENDPOINT = MESSAGE_BASE_URL + "/publication";
  private static final String EXPECTED_PUBLICATION_RESPONSE =
      """
          {
            "messageKey": "123",
            "tenantId": "<default>"
          }""";
  @MockitoBean MessageServices messageServices;
  @MockitoBean MultiTenancyConfiguration multiTenancyCfg;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @Captor ArgumentCaptor<CorrelateMessageRequest> correlationRequestCaptor;
  @Captor ArgumentCaptor<PublicationMessageRequest> publicationRequestCaptor;

  @BeforeEach
  void setup() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(messageServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(messageServices);
  }

  @Test
  void shouldCorrelateMessageWithMultiTenancyDisabled() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(false);
    when(messageServices.correlateMessage(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new MessageCorrelationRecord()
                    .setMessageKey(123L)
                    .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
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

    Mockito.verify(messageServices).correlateMessage(correlationRequestCaptor.capture());
    final var capturedRequest = correlationRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("messageName");
    assertThat(capturedRequest.correlationKey()).isEqualTo("correlationKey");
    assertThat(capturedRequest.variables()).containsExactly(Map.entry("key", "value"));
    assertThat(capturedRequest.tenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    response
        .expectBody()
        .json(
            """
                {
                  "messageKey": "123",
                  "tenantId": "<default>",
                  "processInstanceKey": "321"
                }""",
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldCorrelateMessageWithMultiTenancyEnabled() {
    // given
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_NON_DEFAULT_TENANT);
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    when(messageServices.correlateMessage(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new MessageCorrelationRecord()
                    .setMessageKey(123L)
                    .setTenantId("tenantId")
                    .setProcessInstanceKey(321L)));

    final var request =
        """
            {
              "name": "messageName",
              "correlationKey": "correlationKey",
              "variables": {
                "key": "value"
              },
              "tenantId": "tenantId"
            }""";

    // when then
    final ResponseSpec response =
        webClient
            .post()
            .uri(CORRELATION_ENDPOINT)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk();

    Mockito.verify(messageServices).correlateMessage(correlationRequestCaptor.capture());
    final var capturedRequest = correlationRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("messageName");
    assertThat(capturedRequest.correlationKey()).isEqualTo("correlationKey");
    assertThat(capturedRequest.variables()).containsExactly(Map.entry("key", "value"));
    assertThat(capturedRequest.tenantId()).isEqualTo("tenantId");

    response
        .expectBody()
        .json(
            """
                {
                  "messageKey": "123",
                  "tenantId": "tenantId",
                  "processInstanceKey": "321"
                }""",
            JsonCompareMode.STRICT);
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
                .formatted(CORRELATION_ENDPOINT),
            JsonCompareMode.STRICT);
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
                .formatted(CORRELATION_ENDPOINT),
            JsonCompareMode.STRICT);
    verifyNoInteractions(messageServices);
  }

  @Test
  void shouldRejectMessageCorrelationWithoutTenantWhenMultiTenancyEnabled() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);

    final var request =
        """
            {
              "name": "messageName"
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
              "detail": "Expected to handle request Correlate Message with multi-tenancy enabled, but no tenant identifier was provided.",
              "instance": "%s"
            }"""
                .formatted(CORRELATION_ENDPOINT),
            JsonCompareMode.STRICT);
    verifyNoInteractions(messageServices);
  }

  @Test
  void shouldRejectMessageCorrelationWithTenantWhenMultiTenancyDisabled() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(false);

    final var request =
        """
            {
              "name": "messageName",
              "tenantId": "tenant"
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
                  "detail": "Expected to handle request Correlate Message with tenant identifier 'tenant', but multi-tenancy is disabled",
                  "instance": "%s"
                }"""
                .formatted(CORRELATION_ENDPOINT),
            JsonCompareMode.STRICT);
    verifyNoInteractions(messageServices);
  }

  @Test
  void shouldRejectMessageCorrelationWithTooLongTenantWhenMultiTenancyEnabled() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);

    final var request =
        """
            {
              "name": "messageName",
              "tenantId": "tenanttenanttenanttenanttenanttenanttenanttenanttenant"
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
                  "detail": "Expected to handle request Correlate Message with tenant identifier 'tenanttenanttenanttenanttenanttenanttenanttenanttenant', but tenant identifier is longer than 31 characters.",
                  "instance": "%s"
                }"""
                .formatted(CORRELATION_ENDPOINT),
            JsonCompareMode.STRICT);
    verifyNoInteractions(messageServices);
  }

  @Test
  void shouldRejectMessageCorrelationWithInvalidTenantWhenMultiTenancyEnabled() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);

    final var request =
        """
            {
              "name": "messageName",
              "tenantId": "<invalid>"
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
              "detail": "Expected to handle request Correlate Message with tenant identifier '<invalid>', but tenant identifier contains illegal characters.",
              "instance": "%s"
            }"""
                .formatted(CORRELATION_ENDPOINT),
            JsonCompareMode.STRICT);
    verifyNoInteractions(messageServices);
  }

  @Test
  void shouldPublishMessage() {
    // given
    when(messageServices.publishMessage(any())).thenReturn(buildPublishResponse());

    final var request =
        """
            {
              "name": "messageName",
              "correlationKey": "correlationKey",
              "timeToLive": 123,
              "messageId": "messageId",
              "variables": {
                "key": "value"
              },
              "tenantId": "<default>"
            }""";

    // when then
    webClient
        .post()
        .uri(PUBLICATION_ENDPOINT)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_PUBLICATION_RESPONSE, JsonCompareMode.STRICT);

    Mockito.verify(messageServices).publishMessage(publicationRequestCaptor.capture());
    final var capturedRequest = publicationRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("messageName");
    assertThat(capturedRequest.correlationKey()).isEqualTo("correlationKey");
    assertThat(capturedRequest.timeToLive()).isEqualTo(123L);
    assertThat(capturedRequest.messageId()).isEqualTo("messageId");
    assertThat(capturedRequest.variables()).containsExactly(Map.entry("key", "value"));
    assertThat(capturedRequest.tenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  void shouldPublishMessageWithoutCorrelationKey() {
    // given
    when(messageServices.publishMessage(any())).thenReturn(buildPublishResponse());

    final var request =
        """
            {
              "name": "messageName",
              "timeToLive": 123,
              "messageId": "messageId",
              "variables": {
                "key": "value"
              },
              "tenantId": "<default>"
            }""";

    // when then
    webClient
        .post()
        .uri(PUBLICATION_ENDPOINT)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_PUBLICATION_RESPONSE, JsonCompareMode.STRICT);

    Mockito.verify(messageServices).publishMessage(publicationRequestCaptor.capture());
    final var capturedRequest = publicationRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("messageName");
    assertThat(capturedRequest.correlationKey()).isEqualTo("");
    assertThat(capturedRequest.timeToLive()).isEqualTo(123L);
    assertThat(capturedRequest.messageId()).isEqualTo("messageId");
    assertThat(capturedRequest.variables()).containsExactly(Map.entry("key", "value"));
    assertThat(capturedRequest.tenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  void shouldPublishMessageWithoutTimeToLive() {
    // given
    when(messageServices.publishMessage(any())).thenReturn(buildPublishResponse());

    final var request =
        """
            {
              "name": "messageName",
              "messageId": "messageId",
              "variables": {
                "key": "value"
              },
              "tenantId": "<default>"
            }""";

    // when then
    webClient
        .post()
        .uri(PUBLICATION_ENDPOINT)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_PUBLICATION_RESPONSE, JsonCompareMode.STRICT);

    Mockito.verify(messageServices).publishMessage(publicationRequestCaptor.capture());
    final var capturedRequest = publicationRequestCaptor.getValue();
    assertThat(capturedRequest.name()).isEqualTo("messageName");
    assertThat(capturedRequest.correlationKey()).isEqualTo("");
    assertThat(capturedRequest.timeToLive()).isEqualTo(0L);
    assertThat(capturedRequest.messageId()).isEqualTo("messageId");
    assertThat(capturedRequest.variables()).containsExactly(Map.entry("key", "value"));
    assertThat(capturedRequest.tenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  void shouldRejectPublishMessageWithoutName() {
    // given
    when(messageServices.publishMessage(any())).thenReturn(buildPublishResponse());

    final var request =
        """
            {
              "correlationKey": "correlationKey",
              "timeToLive": 123,
              "messageId": "messageId",
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
                "detail":"No name provided.",
                "instance":"/v2/messages/publication"
             }""";

    // when then
    webClient
        .post()
        .uri(PUBLICATION_ENDPOINT)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  private CompletableFuture<BrokerResponse<MessageRecord>> buildPublishResponse() {
    final var record =
        new MessageRecord()
            .setName("messageName")
            .setCorrelationKey("correlationKey")
            .setTimeToLive(123L)
            .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    return CompletableFuture.completedFuture(new BrokerResponse<>(record, 1, 123));
  }
}
