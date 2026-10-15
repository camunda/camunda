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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.GlobalListenerEntity;
import io.camunda.search.entities.GlobalListenerSource;
import io.camunda.search.entities.GlobalListenerType;
import io.camunda.search.query.GlobalListenerQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.service.GlobalListenerServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(value = GlobalTaskListenerController.class)
public class GlobalTaskListenerControllerTest extends RestControllerTest {

  static final Pattern ID_PATTERN =
      Pattern.compile(CamundaSecurityLibraryProperties.DEFAULT_ID_REGEX);
  static final String GLOBAL_TASK_LISTENER_URL = "/v2/global-task-listeners";
  static final String GLOBAL_TASK_LISTENER_WITH_ID_URL = GLOBAL_TASK_LISTENER_URL + "/%s";
  static final String GLOBAL_TASK_LISTENER_SEARCH_URL = GLOBAL_TASK_LISTENER_URL + "/search";

  @Captor ArgumentCaptor<GlobalListenerRecord> listenerRecordCaptor;
  @Captor ArgumentCaptor<GlobalListenerQuery> searchQueryCaptor;
  @MockitoBean GlobalListenerServices globalListenerServices;
  @MockitoBean CamundaSecurityLibraryProperties cslProperties;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean ServiceRegistry serviceRegistry;

  @BeforeEach
  void setupServices() {
    lenient()
        .when(serviceRegistry.globalListenerServices(any()))
        .thenReturn(globalListenerServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(cslProperties.getCompiledIdValidationPattern()).thenReturn(ID_PATTERN);
  }

  @Test
  void shouldGetGlobalTaskListener() {
    // given
    final var entity =
        new GlobalListenerEntity(
            "USER_TASK-my-listener",
            "my-listener",
            "job-type",
            List.of("creating", "updating"),
            3,
            true,
            10,
            GlobalListenerSource.API,
            GlobalListenerType.USER_TASK);

    when(globalListenerServices.getGlobalTaskListener(any(GlobalListenerRecord.class), any()))
        .thenReturn(entity);

    // when / then
    webClient
        .get()
        .uri(GLOBAL_TASK_LISTENER_WITH_ID_URL.formatted("my-listener"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(globalListenerServices).getGlobalTaskListener(listenerRecordCaptor.capture(), any());
    final var capturedRequest = listenerRecordCaptor.getValue();
    assertThat(capturedRequest.getId()).isEqualTo("my-listener");
    assertThat(capturedRequest.getListenerType())
        .isEqualTo(io.camunda.zeebe.protocol.record.value.GlobalListenerType.USER_TASK);
  }

  @Test
  void shouldRejectGetGlobalTaskListenerWithInvalidId() {
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
            .formatted(GLOBAL_TASK_LISTENER_WITH_ID_URL.formatted("$invalid"));

    // when / then
    webClient
        .get()
        .uri(GLOBAL_TASK_LISTENER_WITH_ID_URL.formatted("$invalid"))
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
  void shouldSearchGlobalTaskListeners() {
    // given
    final var entity1 =
        new GlobalListenerEntity(
            "USER_TASK-my-listener-1",
            "my-listener-1",
            "job-type",
            List.of("creating", "updating"),
            3,
            true,
            10,
            GlobalListenerSource.API,
            GlobalListenerType.USER_TASK);

    final var entity2 =
        new GlobalListenerEntity(
            "USER_TASK-my-listener-2",
            "my-listener-2",
            "job-type",
            List.of("creating", "updating"),
            3,
            true,
            10,
            GlobalListenerSource.CONFIGURATION,
            GlobalListenerType.USER_TASK);

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
        .uri(GLOBAL_TASK_LISTENER_SEARCH_URL)
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
  void shouldHandleErrorInGetGlobalTaskListener() {
    // given
    when(globalListenerServices.getGlobalTaskListener(any(GlobalListenerRecord.class), any()))
        .thenThrow(new RuntimeException("Test error"));

    // when / then
    webClient
        .get()
        .uri(GLOBAL_TASK_LISTENER_WITH_ID_URL.formatted("my-listener"))
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
        .uri(GLOBAL_TASK_LISTENER_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }

  @Test
  void shouldCreateGlobalTaskListener() {
    // given
    when(globalListenerServices.createGlobalListener(any(GlobalListenerRecord.class), any()))
        .thenReturn(CompletableFuture.completedFuture(storedListener("my-listener")));

    final var requestBody =
        """
        {
          "id": "my-listener",
          "type": "job-type",
          "eventTypes": ["creating", "updating"],
          "retries": 3,
          "afterNonGlobal": true,
          "priority": 10
        }""";

    // when / then
    webClient
        .post()
        .uri(GLOBAL_TASK_LISTENER_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .json(
            """
            {
              "id": "my-listener",
              "type": "job-type",
              "eventTypes": ["creating", "updating"],
              "retries": 3,
              "afterNonGlobal": true,
              "priority": 10,
              "source": "API"
            }""",
            JsonCompareMode.STRICT);

    verify(globalListenerServices).createGlobalListener(listenerRecordCaptor.capture(), any());
    final var request = listenerRecordCaptor.getValue();
    assertThat(request.getId()).isEqualTo("my-listener");
    assertThat(request.getType()).isEqualTo("job-type");
    assertThat(request.getEventTypes()).containsExactly("creating", "updating");
    assertThat(request.getRetries()).isEqualTo(3);
    assertThat(request.isAfterNonGlobal()).isTrue();
    assertThat(request.getPriority()).isEqualTo(10);
    assertThat(request.getListenerType())
        .isEqualTo(io.camunda.zeebe.protocol.record.value.GlobalListenerType.USER_TASK);
    assertThat(request.getSource())
        .isEqualTo(io.camunda.zeebe.protocol.record.value.GlobalListenerSource.API);
  }

  @Test
  void shouldRejectCreateGlobalTaskListenerWithoutEventTypes() {
    // given
    final var requestBody =
        """
        {
          "id": "my-listener",
          "type": "job-type"
        }""";
    final var expectedBody =
        """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "No eventTypes provided.",
              "instance": "%s"
            }"""
            .formatted(GLOBAL_TASK_LISTENER_URL);

    // when / then
    webClient
        .post()
        .uri(GLOBAL_TASK_LISTENER_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    verify(globalListenerServices, never()).createGlobalListener(any(), any());
  }

  @Test
  void shouldReturnConflictWhenCreatedGlobalTaskListenerAlreadyExists() {
    // given
    when(globalListenerServices.createGlobalListener(any(GlobalListenerRecord.class), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        GlobalListenerIntent.CREATE,
                        1L,
                        RejectionType.ALREADY_EXISTS,
                        "Listener already exists"))));

    final var requestBody =
        """
        {
          "id": "my-listener",
          "type": "job-type",
          "eventTypes": ["creating"]
        }""";

    // when / then
    webClient
        .post()
        .uri(GLOBAL_TASK_LISTENER_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void shouldUpdateGlobalTaskListener() {
    // given
    when(globalListenerServices.updateGlobalListener(any(GlobalListenerRecord.class), any()))
        .thenReturn(CompletableFuture.completedFuture(storedListener("my-listener")));

    final var requestBody =
        """
        {
          "type": "job-type",
          "eventTypes": ["creating", "updating"],
          "retries": 3,
          "afterNonGlobal": true,
          "priority": 10
        }""";

    // when / then
    webClient
        .put()
        .uri(GLOBAL_TASK_LISTENER_WITH_ID_URL.formatted("my-listener"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "id": "my-listener",
              "type": "job-type",
              "eventTypes": ["creating", "updating"],
              "retries": 3,
              "afterNonGlobal": true,
              "priority": 10,
              "source": "API"
            }""",
            JsonCompareMode.STRICT);

    verify(globalListenerServices).updateGlobalListener(listenerRecordCaptor.capture(), any());
    final var request = listenerRecordCaptor.getValue();
    assertThat(request.getId()).isEqualTo("my-listener");
    assertThat(request.getType()).isEqualTo("job-type");
    assertThat(request.getEventTypes()).containsExactly("creating", "updating");
    assertThat(request.getListenerType())
        .isEqualTo(io.camunda.zeebe.protocol.record.value.GlobalListenerType.USER_TASK);
  }

  @Test
  void shouldRejectUpdateGlobalTaskListenerWithInvalidId() {
    // given
    final var requestBody =
        """
        {
          "type": "job-type",
          "eventTypes": ["creating"]
        }""";

    // when / then
    webClient
        .put()
        .uri(GLOBAL_TASK_LISTENER_WITH_ID_URL.formatted("$invalid"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    verify(globalListenerServices, never()).updateGlobalListener(any(), any());
  }

  @Test
  void shouldReturnNotFoundWhenUpdatingUnknownGlobalTaskListener() {
    // given
    when(globalListenerServices.updateGlobalListener(any(GlobalListenerRecord.class), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        GlobalListenerIntent.UPDATE,
                        1L,
                        RejectionType.NOT_FOUND,
                        "Listener not found"))));

    final var requestBody =
        """
        {
          "type": "job-type",
          "eventTypes": ["creating"]
        }""";

    // when / then
    webClient
        .put()
        .uri(GLOBAL_TASK_LISTENER_WITH_ID_URL.formatted("missing"))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void shouldDeleteGlobalTaskListener() {
    // given
    when(globalListenerServices.deleteGlobalListener(any(GlobalListenerRecord.class), any()))
        .thenReturn(CompletableFuture.completedFuture(storedListener("my-listener")));

    // when / then
    webClient
        .delete()
        .uri(GLOBAL_TASK_LISTENER_WITH_ID_URL.formatted("my-listener"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(globalListenerServices).deleteGlobalListener(listenerRecordCaptor.capture(), any());
    final var request = listenerRecordCaptor.getValue();
    assertThat(request.getId()).isEqualTo("my-listener");
    assertThat(request.getListenerType())
        .isEqualTo(io.camunda.zeebe.protocol.record.value.GlobalListenerType.USER_TASK);
  }

  @Test
  void shouldRejectDeleteGlobalTaskListenerWithInvalidId() {
    // when / then
    webClient
        .delete()
        .uri(GLOBAL_TASK_LISTENER_WITH_ID_URL.formatted("$invalid"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);

    verify(globalListenerServices, never()).deleteGlobalListener(any(), any());
  }

  @Test
  void shouldReturnNotFoundWhenDeletingUnknownGlobalTaskListener() {
    // given
    when(globalListenerServices.deleteGlobalListener(any(GlobalListenerRecord.class), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        GlobalListenerIntent.DELETE,
                        1L,
                        RejectionType.NOT_FOUND,
                        "Listener not found"))));

    // when / then
    webClient
        .delete()
        .uri(GLOBAL_TASK_LISTENER_WITH_ID_URL.formatted("missing"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  private static GlobalListenerRecord storedListener(final String id) {
    return new GlobalListenerRecord()
        .setId(id)
        .setType("job-type")
        .setRetries(3)
        .setEventTypes(List.of("creating", "updating"))
        .setAfterNonGlobal(true)
        .setPriority(10)
        .setSource(io.camunda.zeebe.protocol.record.value.GlobalListenerSource.API)
        .setListenerType(io.camunda.zeebe.protocol.record.value.GlobalListenerType.USER_TASK);
  }
}
