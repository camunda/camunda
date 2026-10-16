/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.FormEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.FormServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = FormController.class)
public class FormControllerTest extends RestControllerTest {

  private static final String FORMS_GET_URL = "/v2/forms/%s";

  @MockitoBean FormServices formServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean ServiceRegistry serviceRegistry;

  @BeforeEach
  void setupServices() {
    when(serviceRegistry.formServices(any())).thenReturn(formServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldGetFormByKey() {
    // given
    final var formKey = 1L;
    when(formServices.getByKey(eq(formKey), any()))
        .thenReturn(new FormEntity(formKey, "t", "formId", "{\"components\":[]}", 3L));

    final var expectedResponse =
        """
            {
              "tenantId": "t",
              "formId": "formId",
              "schema": "{\\"components\\":[]}",
              "version": 3,
              "formKey": "1"
            }""";

    // when / then
    webClient
        .get()
        .uri(FORMS_GET_URL.formatted(formKey))
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);

    verify(formServices).getByKey(eq(formKey), any());
  }

  @Test
  void shouldReturn404ForNotFoundForm() {
    // given
    final var formKey = 1L;
    when(formServices.getByKey(eq(formKey), any()))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException(
                    "Form with key 1 was not found.", CamundaSearchException.Reason.NOT_FOUND)));

    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "title": "NOT_FOUND",
              "status": 404,
              "detail": "Form with key 1 was not found.",
              "instance": "%s"
            }"""
            .formatted(FORMS_GET_URL.formatted(formKey));

    // when / then
    webClient
        .get()
        .uri(FORMS_GET_URL.formatted(formKey))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  void shouldReturn500ForUnexpectedError() {
    // given
    final var formKey = 1L;
    when(formServices.getByKey(eq(formKey), any()))
        .thenThrow(new RuntimeException("Failed to get form."));

    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "title": "java.lang.RuntimeException",
              "status": 500,
              "detail": "Unexpected error occurred during the request processing: Failed to get form.",
              "instance": "%s"
            }"""
            .formatted(FORMS_GET_URL.formatted(formKey));

    // when / then
    webClient
        .get()
        .uri(FORMS_GET_URL.formatted(formKey))
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  void shouldReturn400ForInvalidFormKey() {
    // given
    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "title": "Bad Request",
              "status": 400,
              "detail": "Failed to convert 'formKey' with value: 'invalidKey'",
              "instance": "/v2/forms/invalidKey"
            }""";

    // when / then
    webClient
        .get()
        .uri(FORMS_GET_URL.formatted("invalidKey"))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }
}
