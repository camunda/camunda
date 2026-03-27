/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.controller.adapter.DefaultProcessInstanceServiceAdapter;
import io.camunda.zeebe.gateway.rest.controller.generated.GeneratedProcessInstanceController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@Import(DefaultProcessInstanceServiceAdapter.class)
@WebMvcTest(value = {GeneratedProcessInstanceController.class})
public class RestApiJacksonConfigTest extends RestApiConfigurationTest {

  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUpServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(authenticationProvider.getAnonymousIfUnavailable())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "false", "5.5", "[1,2,3]"})
  void shouldYieldBadRequestForQueryViolatingFilterTypeStringRequest(final String invalidValue) {
    // given — processDefinitionKey is a LongKey filter property (oneOf: [string, AdvancedFilter]).
    // The custom deserializer accepts VALUE_STRING and VALUE_NUMBER_INT (long keys can be numeric),
    // but rejects booleans, floats, and arrays.
    final var request =
        """
            {
                "filter": {
                        "processDefinitionKey": %s
                }
            }"""
            .formatted(invalidValue);
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "Bad Request",
                  "status": 400,
                  "detail": "Request property [filter.processDefinitionKey] cannot be parsed",
                  "instance": "%s"
                }""",
            PROCESS_INSTANCES_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);

    verify(processInstanceServices, never()).search(any(ProcessInstanceQuery.class), any());
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "false", "5.5", "[1,2,3]"})
  void shouldYieldBadRequestForQueryViolatingFilterTypeIntRequest(final String invalidValue) {
    // given — processDefinitionVersion is an Integer filter property (oneOf: [integer,
    // AdvancedFilter]).
    // The custom deserializer accepts VALUE_NUMBER_INT and VALUE_STRING (parsed as int),
    // but rejects booleans, floats, and arrays.
    final var request =
        """
            {
                "filter": {
                        "processDefinitionVersion": %s
                }
            }"""
            .formatted(invalidValue);
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "Bad Request",
                  "status": 400,
                  "detail": "Request property [filter.processDefinitionVersion] cannot be parsed",
                  "instance": "%s"
                }""",
            PROCESS_INSTANCES_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);

    verify(processInstanceServices, never()).search(any(ProcessInstanceQuery.class), any());
  }
}
