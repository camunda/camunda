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
import static org.mockito.Mockito.when;

import io.camunda.security.auth.Authentication;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.ElementInstanceServices.SetVariablesRequest;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.cache.ProcessCache;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
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

@WebMvcTest(ElementInstanceController.class)
public class ElementInstanceControllerTest extends RestControllerTest {

  static final String ELEMENTS_BASE_URL = "/v2/element-instances";

  @MockBean ElementInstanceServices elementInstanceServices;
  @MockBean ProcessCache processCache;
  @Captor ArgumentCaptor<SetVariablesRequest> requestCaptor;

  @BeforeEach
  void setup() {
    when(elementInstanceServices.withAuthentication(any(Authentication.class)))
        .thenReturn(elementInstanceServices);
  }

  @Test
  void shouldSetSetVariables() {
    // given
    when(elementInstanceServices.setVariables(any(SetVariablesRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new VariableDocumentRecord()));

    final var request =
        """
            {
              "variables": {
                "key": "value"
              },
              "local": true,
              "operationReference": 123
            }""";

    // when/then
    webClient
        .put()
        .uri(ELEMENTS_BASE_URL + "/123/variables")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(elementInstanceServices).setVariables(requestCaptor.capture());
    final var capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.elementInstanceKey()).isEqualTo(123L);
    assertThat(capturedRequest.variables()).isEqualTo(Map.of("key", "value"));
    assertThat(capturedRequest.local()).isTrue();
    assertThat(capturedRequest.operationReference()).isEqualTo(123L);
  }

  @Test
  void shouldRejectSetVariablesWithSetVariablesNull() {
    // given
    final var request =
        """
            {
                "variables": null
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No variables provided.",
                "instance":"/v2/element-instances/123/variables"
             }""";

    // when / then
    webClient
        .put()
        .uri(ELEMENTS_BASE_URL + "/123/variables")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody);
  }

  @Test
  void shouldRejectSetVariablesWithSetVariablesEmpty() {
    // given
    final var request =
        """
            {
                "variables": {}
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"No variables provided.",
                "instance":"/v2/element-instances/123/variables"
             }""";

    // when / then
    webClient
        .put()
        .uri(ELEMENTS_BASE_URL + "/123/variables")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody);
  }

  @Test
  void shouldRejectSetSetVariablesWithOperationReferenceNotValid() {
    // given
    final var request =
        """
            {
              "variables": {
                "key": "value"
              },
              "operationReference": -123
            }""";

    final var expectedBody =
        """
            {
                "type":"about:blank",
                "title":"INVALID_ARGUMENT",
                "status":400,
                "detail":"The value for operationReference is '-123' but must be > 0.",
                "instance":"/v2/element-instances/123/variables"
             }""";

    // when / then
    webClient
        .put()
        .uri(ELEMENTS_BASE_URL + "/123/variables")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody);
  }
}
