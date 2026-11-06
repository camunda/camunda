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

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(value = ProcessDefinitionController.class)
public class ProcessDefinitionSuspensionControllerTest extends RestControllerTest {

  static final String SUSPEND_URL = "/v2/process-definitions/%s/suspension";
  static final String RESUME_URL = "/v2/process-definitions/%s/resumption";

  @MockitoBean ProcessDefinitionServices processDefinitionServices;
  @MockitoBean ServiceRegistry serviceRegistry;
  @MockitoBean MultiTenancyConfiguration multiTenancyCfg;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setupServices() {
    Mockito.lenient()
        .when(serviceRegistry.processDefinitionServices(any()))
        .thenReturn(processDefinitionServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldSuspendProcessDefinition() {
    // given
    when(processDefinitionServices.suspendProcessDefinition(eq(123L), any()))
        .thenReturn(CompletableFuture.completedFuture(new ProcessDefinitionSuspensionRecord()));

    // when/then
    webClient
        .post()
        .uri(SUSPEND_URL.formatted("123"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(processDefinitionServices).suspendProcessDefinition(eq(123L), any());
  }

  @Test
  void shouldResumeProcessDefinition() {
    // given
    when(processDefinitionServices.resumeProcessDefinition(eq(123L), any()))
        .thenReturn(CompletableFuture.completedFuture(new ProcessDefinitionSuspensionRecord()));

    // when/then
    webClient
        .post()
        .uri(RESUME_URL.formatted("123"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(processDefinitionServices).resumeProcessDefinition(eq(123L), any());
  }

  @Test
  void shouldReturnNotFoundWhenProcessDefinitionDoesNotExist() {
    // given
    final var reason =
        "Expected to suspend process definition with key '123', but no such process definition was found";
    when(processDefinitionServices.suspendProcessDefinition(eq(123L), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException(reason, ServiceException.Status.NOT_FOUND)));

    final var expectedBody =
        """
        {
          "type": "about:blank",
          "title": "NOT_FOUND",
          "status": 404,
          "detail": "%s",
          "instance": "/v2/process-definitions/123/suspension"
        }"""
            .formatted(reason);

    // when/then
    webClient
        .post()
        .uri(SUSPEND_URL.formatted("123"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldReturnConflictOnInvalidState() {
    // given
    final var reason =
        "Expected to resume process definition with key '123', but it is not suspended";
    when(processDefinitionServices.resumeProcessDefinition(eq(123L), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException(reason, ServiceException.Status.INVALID_STATE)));

    final var expectedBody =
        """
        {
          "type": "about:blank",
          "title": "INVALID_STATE",
          "status": 409,
          "detail": "%s",
          "instance": "/v2/process-definitions/123/resumption"
        }"""
            .formatted(reason);

    // when/then
    webClient
        .post()
        .uri(RESUME_URL.formatted("123"))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }
}
