/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.service.JobServices;
import io.camunda.service.JobServices.UpdateJobChangeset;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResponse;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@WebMvcTest(JobController.class)
public class JobControllerTest extends RestControllerTest {

  static final String JOBS_BASE_URL = "/v2/jobs";

  @MockBean JobServices<JobActivationResponse> jobServices;
  @MockBean MultiTenancyCfg multiTenancyCfg;
  @MockBean ResponseObserverProvider responseObserverProvider;

  @BeforeEach
  void setup() {
    when(jobServices.withAuthentication(any(Authentication.class))).thenReturn(jobServices);
  }

  @Test
  void shouldFailJob() {
    // given
    when(jobServices.failJob(anyLong(), anyInt(), anyString(), anyLong(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
        {
          "retries": 1,
          "errorMessage": "error",
          "retryBackOff": 1,
          "variables": {
            "foo": "bar"
          }
        }""";
    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/1/failure")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(jobServices).failJob(1L, 1, "error", 1L, Map.of("foo", "bar"));
  }

  @Test
  void shouldFailJobWithoutBody() {
    // given
    when(jobServices.failJob(anyLong(), anyInt(), anyString(), anyLong(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/1/failure")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(jobServices).failJob(1L, 0, "", 0L, Map.of());
  }

  @Test
  void shouldFailJobWithEmptyBody() {
    // given
    when(jobServices.failJob(anyLong(), anyInt(), anyString(), anyLong(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
        {}
        """;

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/1/failure")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(jobServices).failJob(1L, 0, "", 0L, null);
  }

  @Test
  void shouldThrowErrorJob() {
    // given
    when(jobServices.errorJob(anyLong(), anyString(), anyString(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
        {
          "errorCode": "400",
          "errorMessage": "error",
          "variables": {
            "foo": "bar"
          }
        }""";
    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/1/error")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(jobServices).errorJob(1L, "400", "error", Map.of("foo", "bar"));
  }

  @Test
  void shouldRejectThrowErrorJobWithoutBody() {
    // given
    final var expectedBody =
        """
        {
          "type": "about:blank",
          "status": 400,
          "title": "Bad Request",
          "detail": "Required request body is missing",
          "instance": "%s"
        }"""
            .formatted(JOBS_BASE_URL + "/1/error");

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/1/error")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody);
  }

  @Test
  void shouldRejectThrowErrorJobWithoutErrorCode() {
    // given
    final var request =
        """
        {
          "errorMessage": "error",
          "variables": {
            "foo": "bar"
          }
        }""";

    final var expectedBody =
        """
        {
          "type": "about:blank",
          "status": 400,
          "title": "INVALID_ARGUMENT",
          "detail": "No errorCode provided.",
          "instance": "%s"
        }"""
            .formatted(JOBS_BASE_URL + "/1/error");

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/1/error")
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
  void shouldRejectThrowErrorJobWithEmptyErrorCode() {
    // given
    final var request =
        """
        {
          "errorCode": "",
          "errorMessage": "error",
          "variables": {
            "foo": "bar"
          }
        }""";

    final var expectedBody =
        """
        {
          "type": "about:blank",
          "status": 400,
          "title": "INVALID_ARGUMENT",
          "detail": "No errorCode provided.",
          "instance": "%s"
        }"""
            .formatted(JOBS_BASE_URL + "/1/error");

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/1/error")
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
  void shouldRejectThrowErrorJobWithOnlySpacesErrorCode() {
    // given
    final var request =
        """
        {
          "errorCode": "    ",
          "errorMessage": "error",
          "variables": {
            "foo": "bar"
          }
        }""";

    final var expectedBody =
        """
        {
          "type": "about:blank",
          "status": 400,
          "title": "INVALID_ARGUMENT",
          "detail": "No errorCode provided.",
          "instance": "%s"
        }"""
            .formatted(JOBS_BASE_URL + "/1/error");

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/1/error")
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
  void shouldCompleteJob() {
    // given
    when(jobServices.completeJob(anyLong(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(jobServices).completeJob(1L, Map.of());
  }

  @Test
  void shouldCompleteJobWithVariables() {
    // given
    when(jobServices.completeJob(anyLong(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
        {
          "variables": {
            "foo": "bar"
          }
        }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(jobServices).completeJob(1L, Map.of("foo", "bar"));
  }

  @Test
  void shouldUpdateJob() {
    // given
    when(jobServices.updateJob(anyLong(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
        {
          "changeset": {
            "retries": 5,
            "timeout": 1000
          }
        }""";
    // when/then
    webClient
        .patch()
        .uri(JOBS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(jobServices).updateJob(1L, new UpdateJobChangeset(5, 1000L));
  }

  @Test
  void shouldUpdateJobWithOnlyRetries() {
    // given
    when(jobServices.updateJob(anyLong(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
        {
          "changeset": {
            "retries": 5
          }
        }""";
    // when/then
    webClient
        .patch()
        .uri(JOBS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(jobServices).updateJob(1L, new UpdateJobChangeset(5, null));
  }

  @Test
  void shouldUpdateJobWithOnlyTimeout() {
    // given
    when(jobServices.updateJob(anyLong(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
        {
          "changeset": {
            "timeout": 1000
          }
        }""";
    // when/then
    webClient
        .patch()
        .uri(JOBS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent();

    Mockito.verify(jobServices).updateJob(1L, new UpdateJobChangeset(null, 1000L));
  }

  @Test
  void shouldRejectUpdateJob() {
    // given
    final var request =
        """
        {
          "changeset": {}
        }""";

    final var expectedBody =
        """
        {
          "type": "about:blank",
          "status": 400,
          "title": "INVALID_ARGUMENT",
          "detail": "At least one of [retries, timeout] is required.",
          "instance": "%s"
        }"""
            .formatted(JOBS_BASE_URL + "/1");

    // when/then
    webClient
        .patch()
        .uri(JOBS_BASE_URL + "/1")
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
  void shouldRejectUpdateJobNoBody() {
    // given
    final var expectedBody =
        """
        {
          "type": "about:blank",
          "status": 400,
          "title": "Bad Request",
          "detail": "Required request body is missing",
          "instance": "%s"
        }"""
            .formatted(JOBS_BASE_URL + "/1");

    // when/then
    webClient
        .patch()
        .uri(JOBS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody);
  }

  @ParameterizedTest
  @MethodSource("tenantLists")
  void shouldRejectActivateJobWithUnauthorizedTenantWhenMultiTenancyEnabled(
      final List<String> tenantIds) {
    // given
    when(multiTenancyCfg.isEnabled()).thenReturn(true);
    final var request =
        """
        {
          "type": "TEST",
          "maxJobsToActivate": 2,
          "requestTimeout": 100,
          "timeout": 100,
          "fetchVariable": [],
          "tenantIds": %s,
          "worker": "bar"
        }"""
            .formatted(tenantIds.stream().map("\"%s\""::formatted).toList());

    // when then
    final ResponseSpec response =
        withMultiTenancy(
            "tenantId",
            client ->
                client
                    .post()
                    .uri(JOBS_BASE_URL + "/activation")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus()
                    .isUnauthorized());
    response
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "status": 401,
              "title": "UNAUTHORIZED",
              "detail": "Expected to handle request Activate Jobs with tenant %s, but the user is not authorized for that tenant",
              "instance": "%s"
            }"""
                .formatted(
                    tenantIds.size() == 1
                        ? "identifier '" + tenantIds.getFirst() + "'"
                        : "identifiers " + tenantIds,
                    JOBS_BASE_URL + "/activation"));
    verifyNoInteractions(jobServices);
  }

  @ParameterizedTest
  @MethodSource("tenantLists")
  void shouldRejectActivateJobWithTenantsWhenMultiTenancyDisabled(final List<String> tenantIds) {
    // given
    when(multiTenancyCfg.isEnabled()).thenReturn(false);
    final var request =
        """
        {
          "type": "TEST",
          "maxJobsToActivate": 2,
          "requestTimeout": 100,
          "timeout": 100,
          "fetchVariable": [],
          "tenantIds": %s,
          "worker": "bar"
        }"""
            .formatted(tenantIds.stream().map("\"%s\""::formatted).toList());

    // when then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/activation")
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
              "detail": "Expected to handle request Activate Jobs with tenant %s, but multi-tenancy is disabled",
              "instance": "%s"
            }"""
                .formatted(
                    tenantIds.size() == 1
                        ? "identifier '" + tenantIds.getFirst() + "'"
                        : "identifiers " + tenantIds,
                    JOBS_BASE_URL + "/activation"));
    verifyNoInteractions(jobServices);
  }

  static Stream<Arguments> tenantLists() {
    return Stream.of(
        Arguments.of(List.of("unauthorizedTenant")),
        Arguments.of(List.of("tenantId", "unauthorizedTenant")),
        Arguments.of(List.of("tenantId", "<default>")));
  }
}
