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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.config.MultiTenancyConfiguration;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.ProcessDefinitionServices.ProcessDefinitionStateChangeRequest;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionRecord;
import io.camunda.zeebe.protocol.impl.record.value.processdefinition.ProcessDefinitionSuspensionResolvedVersion;
import io.camunda.zeebe.protocol.record.value.ProcessDefinitionSuspensionOutcome;
import java.util.concurrent.CompletableFuture;
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
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(value = ProcessDefinitionController.class)
public class ProcessDefinitionSuspensionControllerTest extends RestControllerTest {

  static final String SUSPENSION_URL = "/v2/process-definitions/suspension";
  static final String RESUMPTION_URL = "/v2/process-definitions/resumption";

  @Captor ArgumentCaptor<ProcessDefinitionStateChangeRequest> requestCaptor;
  @MockitoBean ProcessDefinitionServices processDefinitionServices;
  @MockitoBean ServiceRegistry serviceRegistry;
  @MockitoBean MultiTenancyConfiguration multiTenancyCfg;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setupServices() {
    lenient()
        .when(serviceRegistry.processDefinitionServices(any()))
        .thenReturn(processDefinitionServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldSuspendProcessDefinitionByKey() {
    // given
    when(processDefinitionServices.suspendProcessDefinitions(any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                record(11L, 1, ProcessDefinitionSuspensionOutcome.SUSPENDED)));

    // when
    final ResponseSpec response =
        post(
            SUSPENSION_URL,
            """
        {"processDefinitionKey": "11"}""");

    // then
    response
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "processDefinitions": [
                {"processDefinitionKey": "11", "version": 1, "outcome": "SUSPENDED"}
              ],
              "batchOperationKey": null
            }""",
            JsonCompareMode.STRICT);
    verify(processDefinitionServices).suspendProcessDefinitions(requestCaptor.capture(), any());
    assertThat(requestCaptor.getValue())
        .isEqualTo(new ProcessDefinitionStateChangeRequest(11L, "", -1, "<default>", false));
  }

  @Test
  void shouldSuspendAllVersionsByIdAndCascadeToInstances() {
    // given
    final var result =
        record(11L, 1, ProcessDefinitionSuspensionOutcome.ALREADY_SUSPENDED)
            .addResolvedVersion(
                new ProcessDefinitionSuspensionResolvedVersion()
                    .setProcessDefinitionKey(12L)
                    .setVersion(2)
                    .setOutcome(ProcessDefinitionSuspensionOutcome.SUSPENDED))
            .setBatchOperationKey(99L);
    when(processDefinitionServices.suspendProcessDefinitions(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(result));

    // when
    final ResponseSpec response =
        post(
            SUSPENSION_URL,
            """
            {"processDefinitionId": "invoice", "includeProcessInstances": true}""");

    // then
    response
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "processDefinitions": [
                {"processDefinitionKey": "11", "version": 1, "outcome": "ALREADY_SUSPENDED"},
                {"processDefinitionKey": "12", "version": 2, "outcome": "SUSPENDED"}
              ],
              "batchOperationKey": "99"
            }""",
            JsonCompareMode.STRICT);
    verify(processDefinitionServices).suspendProcessDefinitions(requestCaptor.capture(), any());
    assertThat(requestCaptor.getValue())
        .isEqualTo(new ProcessDefinitionStateChangeRequest(-1L, "invoice", -1, "<default>", true));
  }

  @Test
  void shouldSuspendSingleVersionByIdAndVersion() {
    // given
    when(processDefinitionServices.suspendProcessDefinitions(any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                record(12L, 2, ProcessDefinitionSuspensionOutcome.SUSPENDED)));

    // when
    final ResponseSpec response =
        post(
            SUSPENSION_URL,
            """
            {"processDefinitionId": "invoice", "processDefinitionVersion": 2}""");

    // then
    response.expectStatus().isOk();
    verify(processDefinitionServices).suspendProcessDefinitions(requestCaptor.capture(), any());
    assertThat(requestCaptor.getValue())
        .isEqualTo(new ProcessDefinitionStateChangeRequest(-1L, "invoice", 2, "<default>", false));
  }

  @Test
  void shouldAcceptSuspensionRequestWithoutContentType() {
    // given
    when(processDefinitionServices.suspendProcessDefinitions(any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                record(11L, 1, ProcessDefinitionSuspensionOutcome.SUSPENDED)));

    // when / then: consumes = {} must not turn a request without content type into a 415
    webClient
        .post()
        .uri(SUSPENSION_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isNotEqualTo(415));
  }

  @Test
  void shouldResumeProcessDefinitionByKey() {
    // given
    when(processDefinitionServices.resumeProcessDefinitions(any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                record(11L, 1, ProcessDefinitionSuspensionOutcome.RESUMED)));

    // when
    final ResponseSpec response =
        post(
            RESUMPTION_URL,
            """
        {"processDefinitionKey": "11"}""");

    // then
    response
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "processDefinitions": [
                {"processDefinitionKey": "11", "version": 1, "outcome": "RESUMED"}
              ],
              "batchOperationKey": null
            }""",
            JsonCompareMode.STRICT);
    verify(processDefinitionServices).resumeProcessDefinitions(requestCaptor.capture(), any());
    assertThat(requestCaptor.getValue())
        .isEqualTo(new ProcessDefinitionStateChangeRequest(11L, "", -1, "<default>", false));
  }

  @Test
  void shouldResumeByIdWithVersionAndCascade() {
    // given
    when(processDefinitionServices.resumeProcessDefinitions(any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                record(12L, 2, ProcessDefinitionSuspensionOutcome.ALREADY_RESUMED)
                    .setBatchOperationKey(7L)));

    // when
    final ResponseSpec response =
        post(
            RESUMPTION_URL,
            """
            {
              "processDefinitionId": "invoice",
              "processDefinitionVersion": 2,
              "includeProcessInstances": true
            }""");

    // then
    response
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.processDefinitions[0].outcome")
        .isEqualTo("ALREADY_RESUMED")
        .jsonPath("$.batchOperationKey")
        .isEqualTo("7");
    verify(processDefinitionServices).resumeProcessDefinitions(requestCaptor.capture(), any());
    assertThat(requestCaptor.getValue())
        .isEqualTo(new ProcessDefinitionStateChangeRequest(-1L, "invoice", 2, "<default>", true));
  }

  @Test
  void shouldRejectSuspensionWithInvalidKey() {
    // when
    final ResponseSpec response =
        post(
            SUSPENSION_URL,
            """
        {"processDefinitionKey": "abc"}""");

    // then
    response.expectStatus().isBadRequest();
    verifyNoInteractions(processDefinitionServices);
  }

  @Test
  void shouldRejectResumptionWithInvalidVersion() {
    // when
    final ResponseSpec response =
        post(
            RESUMPTION_URL,
            """
            {"processDefinitionId": "invoice", "processDefinitionVersion": 0}""");

    // then
    response.expectStatus().isBadRequest();
    verifyNoInteractions(processDefinitionServices);
  }

  @Test
  void shouldRejectRequestWithoutKeyOrId() {
    // when
    final ResponseSpec response = post(SUSPENSION_URL, "{}");

    // then
    response.expectStatus().isBadRequest();
    verifyNoInteractions(processDefinitionServices);
  }

  @Test
  void shouldMapForbidden() {
    // given
    when(processDefinitionServices.suspendProcessDefinitions(any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(new ServiceException("denied", Status.FORBIDDEN)));

    // when / then
    post(
            SUSPENSION_URL,
            """
        {"processDefinitionKey": "11"}""")
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void shouldMapNotFound() {
    // given
    when(processDefinitionServices.resumeProcessDefinitions(any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(new ServiceException("no such", Status.NOT_FOUND)));

    // when / then
    post(
            RESUMPTION_URL,
            """
        {"processDefinitionId": "unknown"}""")
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("NOT_FOUND")
        .jsonPath("$.detail")
        .isEqualTo("no such");
  }

  @Test
  void shouldMapInvalidStateToConflict() {
    // given
    when(processDefinitionServices.suspendProcessDefinitions(any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ServiceException("not in a suspendable state", Status.INVALID_STATE)));

    // when / then
    post(
            SUSPENSION_URL,
            """
        {"processDefinitionKey": "11"}""")
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("INVALID_STATE");
  }

  private ResponseSpec post(final String uri, final String body) {
    return webClient
        .post()
        .uri(uri)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .exchange();
  }

  private static ProcessDefinitionSuspensionRecord record(
      final long key, final int version, final ProcessDefinitionSuspensionOutcome outcome) {
    return new ProcessDefinitionSuspensionRecord()
        .addResolvedVersion(
            new ProcessDefinitionSuspensionResolvedVersion()
                .setProcessDefinitionKey(key)
                .setVersion(version)
                .setOutcome(outcome));
  }
}
