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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.GlobalListenerEntity;
import io.camunda.search.entities.GlobalListenerSource;
import io.camunda.search.entities.GlobalListenerType;
import io.camunda.search.query.GlobalListenerQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.GlobalListenerServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(value = GlobalExecutionListenerController.class)
public class GlobalExecutionListenerControllerTest extends RestControllerTest {

  static final Pattern ID_PATTERN = Pattern.compile(SecurityConfiguration.DEFAULT_ID_REGEX);
  static final String GLOBAL_EXECUTION_LISTENER_URL = "/v2/global-execution-listeners";
  static final String GLOBAL_EXECUTION_LISTENER_WITH_ID_URL = GLOBAL_EXECUTION_LISTENER_URL + "/%s";
  static final String GLOBAL_EXECUTION_LISTENER_SEARCH_URL =
      GLOBAL_EXECUTION_LISTENER_URL + "/search";

  @Captor ArgumentCaptor<GlobalListenerRecord> listenerRecordCaptor;
  @Captor ArgumentCaptor<GlobalListenerQuery> searchQueryCaptor;
  @MockitoBean GlobalListenerServices globalListenerServices;
  @MockitoBean SecurityConfiguration securityConfiguration;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(securityConfiguration.getCompiledIdValidationPattern()).thenReturn(ID_PATTERN);
  }

  @Test
  void shouldGetGlobalExecutionListener() {
    // given
    final var entity =
        new GlobalListenerEntity(
            "EXECUTION_LISTENER-my-listener",
            "my-listener",
            "job-type",
            List.of("start", "end"),
            3,
            true,
            10,
            GlobalListenerSource.API,
            GlobalListenerType.EXECUTION_LISTENER,
            List.of("serviceTask"),
            List.of("tasks"));

    when(globalListenerServices.getGlobalTaskListener(any(GlobalListenerRecord.class), any()))
        .thenReturn(entity);

    // when / then
    webClient
        .get()
        .uri(GLOBAL_EXECUTION_LISTENER_WITH_ID_URL.formatted("my-listener"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(globalListenerServices).getGlobalTaskListener(listenerRecordCaptor.capture(), any());
    final var capturedRequest = listenerRecordCaptor.getValue();
    assertThat(capturedRequest.getId()).isEqualTo("my-listener");
    assertThat(capturedRequest.getListenerType())
        .isEqualTo(io.camunda.zeebe.protocol.record.value.GlobalListenerType.EXECUTION_LISTENER);
  }

  @Test
  void shouldRejectGetGlobalExecutionListenerWithInvalidId() {
    // given
    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "The provided id contains illegal characters. It must match the pattern '^[a-zA-Z0-9_~@.+-]+$'.",
              "instance": "%s"
            }"""
            .formatted(GLOBAL_EXECUTION_LISTENER_WITH_ID_URL.formatted("$invalid"));

    // when / then
    webClient
        .get()
        .uri(GLOBAL_EXECUTION_LISTENER_WITH_ID_URL.formatted("$invalid"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldSearchGlobalExecutionListeners() {
    // given
    final var entity1 =
        new GlobalListenerEntity(
            "EXECUTION_LISTENER-my-listener-1",
            "my-listener-1",
            "job-type",
            List.of("start"),
            3,
            false,
            10,
            GlobalListenerSource.API,
            GlobalListenerType.EXECUTION_LISTENER,
            List.of("serviceTask"),
            List.of("tasks"));

    final var entity2 =
        new GlobalListenerEntity(
            "EXECUTION_LISTENER-my-listener-2",
            "my-listener-2",
            "job-type",
            List.of("end"),
            3,
            true,
            20,
            GlobalListenerSource.CONFIGURATION,
            GlobalListenerType.EXECUTION_LISTENER,
            List.of(),
            List.of("all"));

    final var searchResult =
        new SearchQueryResult.Builder<GlobalListenerEntity>()
            .total(2)
            .items(List.of(entity1, entity2))
            .build();

    when(globalListenerServices.search(any(GlobalListenerQuery.class), any()))
        .thenReturn(searchResult);

    final var requestBody =
        """
        {
          "filter": {}
        }""";

    // when / then
    webClient
        .post()
        .uri(GLOBAL_EXECUTION_LISTENER_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isOk();

    verify(globalListenerServices).search(searchQueryCaptor.capture(), any());
    assertThat(searchQueryCaptor.getValue()).isNotNull();
  }

  @Test
  void shouldHandleErrorInGetGlobalExecutionListener() {
    // given
    when(globalListenerServices.getGlobalTaskListener(any(GlobalListenerRecord.class), any()))
        .thenThrow(new RuntimeException("Test error"));

    // when / then
    webClient
        .get()
        .uri(GLOBAL_EXECUTION_LISTENER_WITH_ID_URL.formatted("my-listener"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }

  @Test
  void shouldHandleErrorInSearch() {
    // given
    when(globalListenerServices.search(any(GlobalListenerQuery.class), any()))
        .thenThrow(new RuntimeException("Test error"));

    final var requestBody =
        """
        {
          "filter": {}
        }""";

    // when / then
    webClient
        .post()
        .uri(GLOBAL_EXECUTION_LISTENER_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }

  @Test
  void shouldCreateGlobalExecutionListener() {
    // given
    final var record = new GlobalListenerRecord();
    record.setId("my-listener");
    when(globalListenerServices.createGlobalListener(any(GlobalListenerRecord.class), any()))
        .thenReturn(CompletableFuture.completedFuture(record));

    final var requestBody =
        """
        {
          "id": "my-listener",
          "type": "job-type",
          "eventTypes": ["start"]
        }""";

    // when / then
    webClient
        .post()
        .uri(GLOBAL_EXECUTION_LISTENER_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isCreated();

    verify(globalListenerServices).createGlobalListener(listenerRecordCaptor.capture(), any());
    final var captured = listenerRecordCaptor.getValue();
    assertThat(captured.getId()).isEqualTo("my-listener");
    assertThat(captured.getType()).isEqualTo("job-type");
    assertThat(captured.getEventTypes()).containsExactly("start");
    assertThat(captured.getListenerType())
        .isEqualTo(io.camunda.zeebe.protocol.record.value.GlobalListenerType.EXECUTION_LISTENER);
  }

  @Test
  void shouldRejectCreateWithInvalidId() {
    // given
    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "instance": "%s"
            }"""
            .formatted(GLOBAL_EXECUTION_LISTENER_URL);

    final var requestBody =
        """
        {
          "id": "$invalid!",
          "type": "job-type",
          "eventTypes": ["start"]
        }""";

    // when / then
    webClient
        .post()
        .uri(GLOBAL_EXECUTION_LISTENER_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status").isEqualTo(400);
  }

  @Test
  void shouldRejectCreateWithMissingEventTypes() {
    // given
    final var requestBody =
        """
        {
          "id": "my-listener",
          "type": "job-type",
          "eventTypes": []
        }""";

    // when / then
    webClient
        .post()
        .uri(GLOBAL_EXECUTION_LISTENER_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.detail").value(detail -> assertThat(detail.toString()).contains("eventTypes"));
  }

  @Test
  void shouldUpdateGlobalExecutionListener() {
    // given
    final var record = new GlobalListenerRecord();
    record.setId("my-listener");
    when(globalListenerServices.updateGlobalListener(any(GlobalListenerRecord.class), any()))
        .thenReturn(CompletableFuture.completedFuture(record));

    final var requestBody =
        """
        {
          "type": "updated-job-type",
          "eventTypes": ["end"]
        }""";

    // when / then
    webClient
        .put()
        .uri(GLOBAL_EXECUTION_LISTENER_WITH_ID_URL.formatted("my-listener"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isOk();

    verify(globalListenerServices).updateGlobalListener(listenerRecordCaptor.capture(), any());
    final var captured = listenerRecordCaptor.getValue();
    assertThat(captured.getId()).isEqualTo("my-listener");
    assertThat(captured.getType()).isEqualTo("updated-job-type");
    assertThat(captured.getEventTypes()).containsExactly("end");
  }

  @Test
  void shouldRejectUpdateWithInvalidId() {
    // given
    final var requestBody =
        """
        {
          "type": "job-type",
          "eventTypes": ["start"]
        }""";

    // when / then
    webClient
        .put()
        .uri(GLOBAL_EXECUTION_LISTENER_WITH_ID_URL.formatted("$invalid"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status").isEqualTo(400);
  }

  @Test
  void shouldDeleteGlobalExecutionListener() {
    // given
    final var record = new GlobalListenerRecord();
    record.setId("my-listener");
    when(globalListenerServices.deleteGlobalListener(any(GlobalListenerRecord.class), any()))
        .thenReturn(CompletableFuture.completedFuture(record));

    // when / then
    webClient
        .delete()
        .uri(GLOBAL_EXECUTION_LISTENER_WITH_ID_URL.formatted("my-listener"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(globalListenerServices).deleteGlobalListener(listenerRecordCaptor.capture(), any());
    final var captured = listenerRecordCaptor.getValue();
    assertThat(captured.getId()).isEqualTo("my-listener");
    assertThat(captured.getListenerType())
        .isEqualTo(io.camunda.zeebe.protocol.record.value.GlobalListenerType.EXECUTION_LISTENER);
  }

  @Test
  void shouldRejectDeleteWithInvalidId() {
    // when / then
    webClient
        .delete()
        .uri(GLOBAL_EXECUTION_LISTENER_WITH_ID_URL.formatted("$invalid"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status").isEqualTo(400);
  }
}
