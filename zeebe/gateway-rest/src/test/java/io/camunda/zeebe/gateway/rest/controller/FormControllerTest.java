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
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.FormServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(value = FormController.class)
public class FormControllerTest extends RestControllerTest {

  static final String FORMS_URL = "/v2/forms/";

  private static final String FORM_ITEM_JSON =
      """
      {
        "formKey": "1",
        "tenantId": "tenant-1",
        "formId": "approval-form",
        "schema": "{\\"components\\":[]}",
        "version": 1
      }
      """;

  @MockitoBean FormServices formServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  public void shouldGetFormByKey() {
    when(formServices.getByKey(eq(1L), any()))
        .thenReturn(new FormEntity(1L, "tenant-1", "approval-form", "{\"components\":[]}", 1L));

    webClient
        .get()
        .uri(FORMS_URL + "1")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(FORM_ITEM_JSON, JsonCompareMode.STRICT);

    verify(formServices).getByKey(eq(1L), any());
  }

  @Test
  public void shouldReturn404WhenFormNotFound() {
    when(formServices.getByKey(eq(999L), any()))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException(
                    "Form with key 999 not found", CamundaSearchException.Reason.NOT_FOUND)));

    webClient
        .get()
        .uri(FORMS_URL + "999")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "title": "NOT_FOUND",
              "status": 404,
              "detail": "Form with key 999 not found",
              "instance": "/v2/forms/999"
            }
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  public void shouldReturn500OnUnexpectedException() {
    when(formServices.getByKey(eq(1L), any())).thenThrow(new RuntimeException("Unexpected error"));

    webClient
        .get()
        .uri(FORMS_URL + "1")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "title": "java.lang.RuntimeException",
              "status": 500,
              "detail": "Unexpected error occurred during the request processing: Unexpected error",
              "instance": "/v2/forms/1"
            }
            """,
            JsonCompareMode.STRICT);
  }
}
