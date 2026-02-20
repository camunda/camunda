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
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.GlobalListenerServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import java.util.List;
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
@WebMvcTest(value = GlobalTaskListenerController.class)
public class GlobalTaskListenerControllerTest extends RestControllerTest {

  static final Pattern ID_PATTERN = Pattern.compile(SecurityConfiguration.DEFAULT_ID_REGEX);
  static final String GLOBAL_TASK_LISTENER_URL = "/v2/global-task-listeners";
  static final String GLOBAL_TASK_LISTENER_WITH_ID_URL = GLOBAL_TASK_LISTENER_URL + "/%s";
  static final String GLOBAL_TASK_LISTENER_SEARCH_URL = GLOBAL_TASK_LISTENER_URL + "/search";

  @Captor ArgumentCaptor<GlobalListenerRecord> listenerRecordCaptor;
  @Captor ArgumentCaptor<GlobalListenerQuery> searchQueryCaptor;
  @MockitoBean GlobalListenerServices globalListenerServices;
  @MockitoBean SecurityConfiguration securityConfiguration;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(globalListenerServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(globalListenerServices);
    when(securityConfiguration.getCompiledIdValidationPattern()).thenReturn(ID_PATTERN);
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

    when(globalListenerServices.getGlobalTaskListener(any(GlobalListenerRecord.class)))
        .thenReturn(entity);

    // when / then
    webClient
        .get()
        .uri(GLOBAL_TASK_LISTENER_WITH_ID_URL.formatted("my-listener"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    verify(globalListenerServices).getGlobalTaskListener(listenerRecordCaptor.capture());
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

    when(globalListenerServices.search(any(GlobalListenerQuery.class))).thenReturn(searchResult);

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

    verify(globalListenerServices).search(searchQueryCaptor.capture());
    assertThat(searchQueryCaptor.getValue()).isNotNull();
  }

  @Test
  void shouldHandleErrorInGetGlobalTaskListener() {
    // given
    when(globalListenerServices.getGlobalTaskListener(any(GlobalListenerRecord.class)))
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
    when(globalListenerServices.search(any(GlobalListenerQuery.class)))
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
}
