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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.Authentication;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.JobServices;
import io.camunda.service.JobServices.UpdateJobChangeset;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResult;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultCorrections;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

@WebMvcTest(JobController.class)
public class JobControllerTest extends RestControllerTest {

  static final String JOBS_BASE_URL = "/v2/jobs";

  @MockBean JobServices<JobActivationResult> jobServices;
  @MockBean MultiTenancyConfiguration multiTenancyCfg;
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

    Mockito.verify(jobServices).failJob(1L, 0, "", 0L, Map.of());
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
    when(jobServices.completeJob(anyLong(), any(), any()))
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

    Mockito.verify(jobServices).completeJob(eq(1L), eq(Map.of()), any(JobResult.class));
  }

  @Test
  void shouldCompleteJobWithResultDeniedTrue() {
    // given
    when(jobServices.completeJob(anyLong(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
          {
            "result": {
              "denied": true,
              "corrections": {}
            }
          }
        """;

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

    final ArgumentCaptor<JobResult> jobResultArgumentCaptor =
        ArgumentCaptor.forClass(JobResult.class);
    Mockito.verify(jobServices)
        .completeJob(eq(1L), eq(Map.of()), jobResultArgumentCaptor.capture());
    assertThat(jobResultArgumentCaptor.getValue().isDenied()).isTrue();
  }

  @Test
  void shouldCompleteJobWithResultDeniedTrueAndDeniedReason() {
    // given
    when(jobServices.completeJob(anyLong(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
          {
            "result": {
              "denied": true,
              "deniedReason": "Reason to deny lifecycle transition",
              "corrections": {}
            }
          }
        """;

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

    final ArgumentCaptor<JobResult> jobResultArgumentCaptor =
        ArgumentCaptor.forClass(JobResult.class);
    Mockito.verify(jobServices)
        .completeJob(eq(1L), eq(Map.of()), jobResultArgumentCaptor.capture());
    assertThat(jobResultArgumentCaptor.getValue().isDenied()).isTrue();
    assertThat(jobResultArgumentCaptor.getValue().getDeniedReason())
        .isEqualTo("Reason to deny lifecycle transition");
  }

  @Test
  void shouldCompleteJobWithResultWithCorrections() {
    // given
    when(jobServices.completeJob(anyLong(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
          {
            "result": {
              "denied": false,
              "corrections": {
                "assignee": "Test",
                "dueDate": "2025-05-23T01:02:03+01:00",
                "followUpDate": "2025-05-25T01:02:03+01:00",
                "candidateUsers": ["UserA", "UserB"],
                "candidateGroups": ["GroupA", "GroupB"],
                "priority": 20
              }
            }
          }
        """;

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

    final ArgumentCaptor<JobResult> jobResultArgumentCaptor =
        ArgumentCaptor.forClass(JobResult.class);
    Mockito.verify(jobServices)
        .completeJob(eq(1L), eq(Map.of()), jobResultArgumentCaptor.capture());

    assertThat(jobResultArgumentCaptor.getValue().getCorrections())
        .isEqualTo(
            new JobResultCorrections()
                .setAssignee("Test")
                .setDueDate("2025-05-23T01:02:03+01:00")
                .setFollowUpDate("2025-05-25T01:02:03+01:00")
                .setCandidateUsersList(List.of("UserA", "UserB"))
                .setCandidateGroupsList(List.of("GroupA", "GroupB"))
                .setPriority(20));

    assertThat(jobResultArgumentCaptor.getValue().getCorrectedAttributes())
        .containsExactly(
            UserTaskRecord.ASSIGNEE,
            UserTaskRecord.DUE_DATE,
            UserTaskRecord.FOLLOW_UP_DATE,
            UserTaskRecord.CANDIDATE_USERS,
            UserTaskRecord.CANDIDATE_GROUPS,
            UserTaskRecord.PRIORITY);
  }

  @Test
  void shouldCompleteJobWithResultWithCorrectionsPartiallySet() {
    // given
    when(jobServices.completeJob(anyLong(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
          {
            "result": {
              "denied": false,
              "corrections": {
                "assignee": "Test",
                "candidateUsers": ["UserA", "UserB"],
                "candidateGroups": ["GroupA", "GroupB"],
                "priority": 20
              }
            }
          }
        """;

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

    final ArgumentCaptor<JobResult> jobResultArgumentCaptor =
        ArgumentCaptor.forClass(JobResult.class);
    Mockito.verify(jobServices)
        .completeJob(eq(1L), eq(Map.of()), jobResultArgumentCaptor.capture());

    assertThat(jobResultArgumentCaptor.getValue().getCorrections())
        .isEqualTo(
            new JobResultCorrections()
                .setAssignee("Test")
                .setCandidateUsersList(List.of("UserA", "UserB"))
                .setCandidateGroupsList(List.of("GroupA", "GroupB"))
                .setPriority(20));

    assertThat(jobResultArgumentCaptor.getValue().getCorrectedAttributes())
        .containsExactly(
            UserTaskRecord.ASSIGNEE,
            UserTaskRecord.CANDIDATE_USERS,
            UserTaskRecord.CANDIDATE_GROUPS,
            UserTaskRecord.PRIORITY);
  }

  @Test
  void shouldCompleteJobWithResultWithCorrectionsPartiallySetAndDefault() {
    // given
    when(jobServices.completeJob(anyLong(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
          {
            "result": {
              "denied": false,
              "corrections": {
                "assignee": null,
                "dueDate": "2025-05-23T01:02:03+01:00",
                "candidateGroups": ["GroupA", "GroupB"],
                "priority": null
              }
            }
          }
        """;

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

    final var jobResultArgumentCaptor = ArgumentCaptor.forClass(JobResult.class);
    Mockito.verify(jobServices)
        .completeJob(eq(1L), eq(Map.of()), jobResultArgumentCaptor.capture());

    assertThat(jobResultArgumentCaptor.getValue().getCorrections())
        .isEqualTo(
            new JobResultCorrections()
                .setDueDate("2025-05-23T01:02:03+01:00")
                .setCandidateGroupsList(List.of("GroupA", "GroupB"))
                // The remaining fields have their default non-null values,
                // as they weren't corrected
                .setAssignee("")
                .setFollowUpDate("")
                .setCandidateUsersList(List.of())
                .setPriority(-1));

    assertThat(jobResultArgumentCaptor.getValue().getCorrectedAttributes())
        .containsExactly(UserTaskRecord.DUE_DATE, UserTaskRecord.CANDIDATE_GROUPS);
  }

  @Test
  void shouldCompleteJobWithResultDeniedFalse() {
    // given
    when(jobServices.completeJob(anyLong(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
          {
            "result": {
              "denied": false,
              "corrections": {}
            }
          }
        """;

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

    final ArgumentCaptor<JobResult> jobResultArgumentCaptor =
        ArgumentCaptor.forClass(JobResult.class);
    Mockito.verify(jobServices)
        .completeJob(eq(1L), eq(Map.of()), jobResultArgumentCaptor.capture());
    assertThat(jobResultArgumentCaptor.getValue().isDenied()).isFalse();
    assertThat(jobResultArgumentCaptor.getValue().getDeniedReason()).isEqualTo("");
  }

  @Test
  void shouldCompleteJobWithResultAndIgnoreUnknownField() {
    // given
    when(jobServices.completeJob(anyLong(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
          {
            "result": {
              "unknownField": true,
              "corrections": {}
            }
          }
        """;

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

    final ArgumentCaptor<JobResult> jobResultArgumentCaptor =
        ArgumentCaptor.forClass(JobResult.class);
    Mockito.verify(jobServices)
        .completeJob(eq(1L), eq(Map.of()), jobResultArgumentCaptor.capture());
    assertThat(jobResultArgumentCaptor.getValue().isDenied()).isFalse();
    assertThat(jobResultArgumentCaptor.getValue().getDeniedReason()).isEqualTo("");
  }

  @Test
  void shouldCompleteJobWithVariables() {
    // given
    when(jobServices.completeJob(anyLong(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
          {
            "variables": {
              "foo": "bar"
            }
          }
        """;

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

    Mockito.verify(jobServices).completeJob(eq(1L), eq(Map.of("foo", "bar")), any(JobResult.class));
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
          }
        """;
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
          }
        """;
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
          }
        """;

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
              "detail": "Expected to handle request Activate Jobs with tenant %s, but tenant is not authorized to perform this request",
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
        Arguments.of(List.of("tenantId", "<default>")),
        Arguments.of(List.of("<default>")));
  }
}
