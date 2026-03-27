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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.gateway.protocol.model.JobActivationResult;
import io.camunda.search.entities.GlobalJobStatisticsEntity;
import io.camunda.search.entities.GlobalJobStatisticsEntity.StatusMetric;
import io.camunda.search.entities.JobErrorStatisticsEntity;
import io.camunda.search.entities.JobTimeSeriesStatisticsEntity;
import io.camunda.search.entities.JobTypeStatisticsEntity;
import io.camunda.search.entities.JobWorkerStatisticsEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.JobServices;
import io.camunda.service.JobServices.ActivateJobsRequest;
import io.camunda.service.JobServices.UpdateJobChangeset;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.controller.adapter.DefaultJobServiceAdapter;
import io.camunda.zeebe.gateway.rest.controller.generated.GeneratedJobController;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultCorrections;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import java.time.OffsetDateTime;
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
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@Import(DefaultJobServiceAdapter.class)
@WebMvcTest(GeneratedJobController.class)
public class JobControllerTest extends RestControllerTest {

  static final String JOBS_BASE_URL = "/v2/jobs";

  @MockitoBean JobServices<JobActivationResult> jobServices;
  @MockitoBean MultiTenancyConfiguration multiTenancyCfg;
  @MockitoBean ResponseObserverProvider responseObserverProvider;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean GatewayRestConfiguration gatewayRestConfiguration;

  @BeforeEach
  void setup() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    final var jobMetricsCfg = new GatewayRestConfiguration.JobMetricsConfiguration();
    jobMetricsCfg.setEnabled(true);
    when(gatewayRestConfiguration.getJobMetrics()).thenReturn(jobMetricsCfg);
  }

  @Test
  void shouldFailJob() {
    // given
    when(jobServices.failJob(anyLong(), anyInt(), anyString(), anyLong(), any(), any()))
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

    Mockito.verify(jobServices)
        .failJob(eq(1L), eq(1), eq("error"), eq(1L), eq(Map.of("foo", "bar")), any());
  }

  @Test
  void shouldFailJobWithoutBody() {
    // given
    when(jobServices.failJob(anyLong(), anyInt(), anyString(), anyLong(), any(), any()))
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
    Mockito.verify(jobServices).failJob(eq(1L), eq(0), eq(""), eq(0L), eq(Map.of()), any());
  }

  @Test
  void shouldFailJobWithEmptyBody() {
    // given
    when(jobServices.failJob(anyLong(), anyInt(), anyString(), anyLong(), any(), any()))
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

    Mockito.verify(jobServices).failJob(eq(1L), eq(0), eq(""), eq(0L), eq(Map.of()), any());
  }

  @Test
  void shouldThrowErrorJob() {
    // given
    when(jobServices.errorJob(anyLong(), anyString(), anyString(), any(), any()))
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

    Mockito.verify(jobServices)
        .errorJob(eq(1L), eq("400"), eq("error"), eq(Map.of("foo", "bar")), any());
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
        .json(expectedBody, JsonCompareMode.STRICT);
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
        .json(expectedBody, JsonCompareMode.STRICT);
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
        .json(expectedBody, JsonCompareMode.STRICT);
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
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldCompleteJob() {
    // given
    when(jobServices.completeJob(anyLong(), any(), any(), any()))
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

    Mockito.verify(jobServices).completeJob(eq(1L), eq(Map.of()), any(JobResult.class), any());
  }

  @Test
  void shouldCompleteJobWithResultDeniedTrue() {
    // given
    when(jobServices.completeJob(anyLong(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
          {
            "result": {
              "type": "userTask",
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
        .completeJob(eq(1L), eq(Map.of()), jobResultArgumentCaptor.capture(), any());
    assertThat(jobResultArgumentCaptor.getValue().isDenied()).isTrue();
  }

  @Test
  void shouldCompleteJobWithResultDeniedTrueAndDeniedReason() {
    // given
    when(jobServices.completeJob(anyLong(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
          {
            "result": {
              "type": "userTask",
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
        .completeJob(eq(1L), eq(Map.of()), jobResultArgumentCaptor.capture(), any());
    assertThat(jobResultArgumentCaptor.getValue().isDenied()).isTrue();
    assertThat(jobResultArgumentCaptor.getValue().getDeniedReason())
        .isEqualTo("Reason to deny lifecycle transition");
  }

  @Test
  void shouldCompleteJobWithResultWithCorrections() {
    // given
    when(jobServices.completeJob(anyLong(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
          {
            "result": {
              "type": "userTask",
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
        .completeJob(eq(1L), eq(Map.of()), jobResultArgumentCaptor.capture(), any());

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
    when(jobServices.completeJob(anyLong(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
          {
            "result": {
              "type": "userTask",
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
        .completeJob(eq(1L), eq(Map.of()), jobResultArgumentCaptor.capture(), any());

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
    when(jobServices.completeJob(anyLong(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
          {
            "result": {
              "type": "userTask",
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
        .completeJob(eq(1L), eq(Map.of()), jobResultArgumentCaptor.capture(), any());

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
    when(jobServices.completeJob(anyLong(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
          {
            "result": {
              "type": "userTask",
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
        .completeJob(eq(1L), eq(Map.of()), jobResultArgumentCaptor.capture(), any());
    assertThat(jobResultArgumentCaptor.getValue().isDenied()).isFalse();
    assertThat(jobResultArgumentCaptor.getValue().getDeniedReason()).isEqualTo("");
  }

  @Test
  void shouldCompleteJobWithVariables() {
    // given
    when(jobServices.completeJob(anyLong(), any(), any(), any()))
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

    Mockito.verify(jobServices)
        .completeJob(eq(1L), eq(Map.of("foo", "bar")), any(JobResult.class), any());
  }

  @Test
  void shouldUpdateJob() {
    // given
    when(jobServices.updateJob(anyLong(), any(), any(), any()))
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

    Mockito.verify(jobServices)
        .updateJob(eq(1L), isNull(), eq(new UpdateJobChangeset(5, 1000L)), any());
  }

  @Test
  void shouldUpdateJobWithOnlyRetries() {
    // given
    when(jobServices.updateJob(anyLong(), any(), any(), any()))
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

    Mockito.verify(jobServices)
        .updateJob(eq(1L), isNull(), eq(new UpdateJobChangeset(5, null)), any());
  }

  @Test
  void shouldUpdateJobWithOnlyTimeout() {
    // given
    when(jobServices.updateJob(anyLong(), any(), any(), any()))
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

    Mockito.verify(jobServices)
        .updateJob(eq(1L), isNull(), eq(new UpdateJobChangeset(null, 1000L)), any());
  }

  @Test
  void shouldUpdateJobWithOperationReference() {
    // given
    when(jobServices.updateJob(anyLong(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(new JobRecord()));

    final var request =
        """
            {
              "changeset": {
                "timeout": 1000
              },
              "operationReference": 12345678
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

    Mockito.verify(jobServices)
        .updateJob(eq(1L), eq(12345678L), eq(new UpdateJobChangeset(null, 1000L)), any());
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
        .json(expectedBody, JsonCompareMode.STRICT);
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
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectActivateJobWithoutTenantsWhenMultiTenancyEnabled() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    final var request =
        """
        {
          "type": "TEST",
          "maxJobsToActivate": 2,
          "requestTimeout": 100,
          "timeout": 100,
          "fetchVariable": [],
          "tenantIds": [],
          "worker": "bar"
        }""";

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
              "detail": "Expected to handle request Activate Jobs with multi-tenancy enabled, but no tenant identifier was provided.",
              "instance": "%s"
            }"""
                .formatted(JOBS_BASE_URL + "/activation"),
            JsonCompareMode.STRICT);
    verifyNoInteractions(jobServices);
  }

  @ParameterizedTest
  @MethodSource("tenantLists")
  void shouldRejectActivateJobWithTenantsWhenMultiTenancyDisabled(final List<String> tenantIds) {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(false);
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
                    JOBS_BASE_URL + "/activation"),
            JsonCompareMode.STRICT);
    verifyNoInteractions(jobServices);
  }

  static Stream<Arguments> tenantLists() {
    return Stream.of(
        Arguments.of(List.of("unauthorizedTenant")),
        Arguments.of(List.of("tenantId", "unauthorizedTenant")),
        Arguments.of(List.of("tenantId", "<default>")));
  }

  @Test
  void shouldGetGlobalJobStatistics() {
    // given
    final var lastUpdatedAt = OffsetDateTime.parse("2024-07-29T15:51:28.071Z");
    final var statisticsEntity =
        new GlobalJobStatisticsEntity(
            new StatusMetric(100, lastUpdatedAt),
            new StatusMetric(80, lastUpdatedAt),
            new StatusMetric(5, lastUpdatedAt),
            false);

    when(jobServices.getGlobalStatistics(any(), any())).thenReturn(statisticsEntity);

    final var expectedResponse =
        """
            {
              "created": {
                "count": 100,
                "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
              },
              "completed": {
                "count": 80,
                "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
              },
              "failed": {
                "count": 5,
                "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
              },
              "isIncomplete": false
            }""";

    // when/then
    webClient
        .get()
        .uri(
            JOBS_BASE_URL
                + "/statistics/global?from=2024-07-28T15:51:28.071Z&to=2024-07-29T15:51:28.071Z")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectGlobalJobStatisticsWithMissingParams() {
    // when/then
    webClient
        .get()
        .uri(JOBS_BASE_URL + "/statistics/global")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectGlobalJobStatisticsWithMissingTo() {
    // when/then
    webClient
        .get()
        .uri(JOBS_BASE_URL + "/statistics/global?from=2024-07-28T15:51:28.071Z")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldGetJobTypeStatistics() {
    // given
    final var lastUpdatedAt = OffsetDateTime.parse("2024-07-29T15:51:28.071Z");
    final var statisticsEntity1 =
        new JobTypeStatisticsEntity(
            "fetch-customer-data",
            new StatusMetric(100, lastUpdatedAt),
            new StatusMetric(80, lastUpdatedAt),
            new StatusMetric(5, lastUpdatedAt),
            3);
    final var statisticsEntity2 =
        new JobTypeStatisticsEntity(
            "process-payment",
            new StatusMetric(50, lastUpdatedAt),
            new StatusMetric(45, lastUpdatedAt),
            new StatusMetric(2, lastUpdatedAt),
            2);

    final var searchResult =
        new SearchQueryResult.Builder<JobTypeStatisticsEntity>()
            .total(2L, true)
            .items(List.of(statisticsEntity1, statisticsEntity2))
            .endCursor("endCursor")
            .build();

    when(jobServices.getJobTypeStatistics(any(), any())).thenReturn(searchResult);

    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:51:28.071Z",
                "to": "2024-07-29T15:51:28.071Z"
              }
            }""";

    final var expectedResponse =
        """
            {
              "items": [
                {
                  "jobType": "fetch-customer-data",
                  "created": {
                    "count": 100,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "completed": {
                    "count": 80,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "failed": {
                    "count": 5,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "workers": 3
                },
                {
                  "jobType": "process-payment",
                  "created": {
                    "count": 50,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "completed": {
                    "count": 45,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "failed": {
                    "count": 2,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "workers": 2
                }
              ],
              "page": {
                "totalItems": 2,
                "startCursor": null,
                "endCursor": "endCursor",
                "hasMoreTotalItems": true
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/by-types")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  void shouldGetJobTypeStatisticsWithJobTypeFilter() {
    // given
    final var lastUpdatedAt = OffsetDateTime.parse("2024-07-29T15:51:28.071Z");
    final var statisticsEntity =
        new JobTypeStatisticsEntity(
            "fetch-customer-data",
            new StatusMetric(100, lastUpdatedAt),
            new StatusMetric(80, lastUpdatedAt),
            new StatusMetric(5, lastUpdatedAt),
            3);

    final var searchResult =
        new SearchQueryResult.Builder<JobTypeStatisticsEntity>()
            .total(1L, false)
            .endCursor("endCursor")
            .items(List.of(statisticsEntity))
            .build();

    when(jobServices.getJobTypeStatistics(any(), any())).thenReturn(searchResult);

    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:51:28.071Z",
                "to": "2024-07-29T15:51:28.071Z",
                "jobType": {
                  "$like": "fetch-*"
                }
              }
            }""";

    final var expectedResponse =
        """
            {
              "items": [
                {
                  "jobType": "fetch-customer-data",
                  "created": {
                    "count": 100,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "completed": {
                    "count": 80,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "failed": {
                    "count": 5,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "workers": 3
                }
              ],
              "page": {
                "totalItems": 1,
                "startCursor": null,
                "endCursor": "endCursor",
                "hasMoreTotalItems": false
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/by-types")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  void shouldGetJobTypeStatisticsWithPagination() {
    // given
    final var lastUpdatedAt = OffsetDateTime.parse("2024-07-29T15:51:28.071Z");
    final var statisticsEntity =
        new JobTypeStatisticsEntity(
            "fetch-customer-data",
            new StatusMetric(100, lastUpdatedAt),
            new StatusMetric(80, lastUpdatedAt),
            new StatusMetric(5, lastUpdatedAt),
            3);

    final var searchResult =
        new SearchQueryResult.Builder<JobTypeStatisticsEntity>()
            .total(1L, false)
            .endCursor("endCursor")
            .items(List.of(statisticsEntity))
            .build();

    when(jobServices.getJobTypeStatistics(any(), any())).thenReturn(searchResult);

    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:51:28.071Z",
                "to": "2024-07-29T15:51:28.071Z"
              },
              "page": {
                "limit": 1
              }
            }""";

    final var expectedResponse =
        """
            {
              "items": [
                {
                  "jobType": "fetch-customer-data",
                  "created": {
                    "count": 100,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "completed": {
                    "count": 80,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "failed": {
                    "count": 5,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "workers": 3
                }
              ],
              "page": {
                "totalItems": 1,
                "startCursor": null,
                "endCursor": "endCursor",
                "hasMoreTotalItems": false
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/by-types")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectJobTypeStatisticsWithMissingBody() {
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
            .formatted(JOBS_BASE_URL + "/statistics/by-types");

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/by-types")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobTypeStatisticsWithMissingFilter() {
    // given
    final var request =
        """
            {
              "page": {
                "limit": 10
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/by-types")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobTypeStatisticsWithEmptyFilter() {
    // given
    final var request =
        """
            {
              "filter": {}
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/by-types")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobTypeStatisticsWithMissingFrom() {
    // given
    final var request =
        """
            {
              "filter": {
                "to": "2024-07-29T15:51:28.071Z"
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/by-types")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobTypeStatisticsWithMissingTo() {
    // given
    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:51:28.071Z"
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/by-types")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldDefaultTenantFilterToProvidedWhenNotSpecified() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    final ArgumentCaptor<ActivateJobsRequest> requestCaptor =
        ArgumentCaptor.forClass(ActivateJobsRequest.class);

    final JobActivationRequestResponseObserver mockObserver =
        Mockito.mock(JobActivationRequestResponseObserver.class);
    when(responseObserverProvider.apply(any()))
        .thenAnswer(
            invocation -> {
              final CompletableFuture<ResponseEntity<Object>> future = invocation.getArgument(0);
              future.complete(ResponseEntity.ok().body(new JobActivationResult(List.of())));
              return mockObserver;
            });

    Mockito.doNothing()
        .when(jobServices)
        .activateJobs(requestCaptor.capture(), any(), any(), any());

    final var request =
        """
        {
          "type": "test-job",
          "maxJobsToActivate": 10,
          "timeout": 5000,
          "tenantIds": ["tenant-a"]
        }""";

    // when
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/activation")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk();

    // then
    final var capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.tenantFilter())
        .describedAs("Should default to PROVIDED when tenantFilter is not specified")
        .isEqualTo(TenantFilter.PROVIDED);
    assertThat(capturedRequest.tenantIds()).containsExactly("tenant-a");
  }

  @Test
  void shouldPassAssignedTenantFilterWithEmptyTenantIdsList() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    final ArgumentCaptor<ActivateJobsRequest> requestCaptor =
        ArgumentCaptor.forClass(ActivateJobsRequest.class);

    final JobActivationRequestResponseObserver mockObserver =
        Mockito.mock(JobActivationRequestResponseObserver.class);
    when(responseObserverProvider.apply(any()))
        .thenAnswer(
            invocation -> {
              final CompletableFuture<ResponseEntity<Object>> future = invocation.getArgument(0);
              future.complete(ResponseEntity.ok().body(new JobActivationResult(List.of())));
              return mockObserver;
            });

    Mockito.doNothing()
        .when(jobServices)
        .activateJobs(requestCaptor.capture(), any(), any(), any());

    final var request =
        """
        {
          "type": "test-job",
          "maxJobsToActivate": 10,
          "timeout": 5000,
          "tenantFilter": "ASSIGNED"
        }""";

    // when
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/activation")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk();

    // then
    final var capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.tenantFilter())
        .describedAs("Should pass ASSIGNED filter through to service layer")
        .isEqualTo(TenantFilter.ASSIGNED);
    assertThat(capturedRequest.tenantIds())
        .describedAs("Tenant IDs should be empty when ASSIGNED filter is used")
        .isEmpty();
  }

  @Test
  void shouldPassAssignedTenantFilterWithEmptyTenantIdsListWhenTenantIdsSupplied() {
    // given
    when(multiTenancyCfg.isChecksEnabled()).thenReturn(true);
    final ArgumentCaptor<ActivateJobsRequest> requestCaptor =
        ArgumentCaptor.forClass(ActivateJobsRequest.class);

    final JobActivationRequestResponseObserver mockObserver =
        Mockito.mock(JobActivationRequestResponseObserver.class);
    when(responseObserverProvider.apply(any()))
        .thenAnswer(
            invocation -> {
              final CompletableFuture<ResponseEntity<Object>> future = invocation.getArgument(0);
              future.complete(ResponseEntity.ok().body(new JobActivationResult(List.of())));
              return mockObserver;
            });

    Mockito.doNothing()
        .when(jobServices)
        .activateJobs(requestCaptor.capture(), any(), any(), any());

    final var request =
        """
        {
          "type": "test-job",
          "maxJobsToActivate": 10,
          "timeout": 5000,
          "tenantIds": ["tenant-a", "tenant-b"],
          "tenantFilter": "ASSIGNED"
        }""";

    // when
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/activation")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk();

    // then
    final var capturedRequest = requestCaptor.getValue();
    assertThat(capturedRequest.tenantFilter())
        .describedAs("Should pass ASSIGNED filter through to service layer")
        .isEqualTo(TenantFilter.ASSIGNED);
    assertThat(capturedRequest.tenantIds())
        .describedAs("Tenant IDs should be empty when ASSIGNED filter is used")
        .isEmpty();
  }

  @Test
  void shouldGetJobWorkerStatistics() {
    // given
    final var lastUpdatedAt = OffsetDateTime.parse("2024-07-29T15:51:28.071Z");
    final var workerEntity1 =
        new JobWorkerStatisticsEntity(
            "worker-1",
            new StatusMetric(400, lastUpdatedAt),
            new StatusMetric(390, lastUpdatedAt),
            new StatusMetric(2, lastUpdatedAt));
    final var workerEntity2 =
        new JobWorkerStatisticsEntity(
            "worker-2",
            new StatusMetric(350, lastUpdatedAt),
            new StatusMetric(340, lastUpdatedAt),
            new StatusMetric(5, lastUpdatedAt));

    final var searchResult =
        new SearchQueryResult.Builder<JobWorkerStatisticsEntity>()
            .total(2L, true)
            .endCursor("endCursor")
            .items(List.of(workerEntity1, workerEntity2))
            .build();

    when(jobServices.getJobWorkerStatistics(any(), any())).thenReturn(searchResult);

    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:51:28.071Z",
                "to": "2024-07-29T15:51:28.071Z",
                "jobType": "fetch-customer-data"
              }
            }""";

    final var expectedResponse =
        """
            {
              "items": [
                {
                  "worker": "worker-1",
                  "created": {
                    "count": 400,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "completed": {
                    "count": 390,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "failed": {
                    "count": 2,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  }
                },
                {
                  "worker": "worker-2",
                  "created": {
                    "count": 350,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "completed": {
                    "count": 340,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "failed": {
                    "count": 5,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  }
                }
              ],
              "page": {
                "totalItems": 2,
                "startCursor": null,
                "endCursor": "endCursor",
                "hasMoreTotalItems": true
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/by-workers")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  void shouldGetJobWorkerStatisticsWithPagination() {
    // given
    final var lastUpdatedAt = OffsetDateTime.parse("2024-07-29T15:51:28.071Z");
    final var workerEntity =
        new JobWorkerStatisticsEntity(
            "worker-1",
            new StatusMetric(400, lastUpdatedAt),
            new StatusMetric(390, lastUpdatedAt),
            new StatusMetric(2, lastUpdatedAt));

    final var searchResult =
        new SearchQueryResult.Builder<JobWorkerStatisticsEntity>()
            .total(1L, false)
            .endCursor("endCursor")
            .items(List.of(workerEntity))
            .build();

    when(jobServices.getJobWorkerStatistics(any(), any())).thenReturn(searchResult);

    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:51:28.071Z",
                "to": "2024-07-29T15:51:28.071Z",
                "jobType": "fetch-customer-data"
              },
              "page": {
                "limit": 1
              }
            }""";

    final var expectedResponse =
        """
            {
              "items": [
                {
                  "worker": "worker-1",
                  "created": {
                    "count": 400,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "completed": {
                    "count": 390,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  },
                  "failed": {
                    "count": 2,
                    "lastUpdatedAt": "2024-07-29T15:51:28.071Z"
                  }
                }
              ],
              "page": {
                "totalItems": 1,
                "startCursor": null,
                "endCursor": "endCursor",
                "hasMoreTotalItems": false
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/by-workers")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectJobWorkerStatisticsWithMissingBody() {
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
            .formatted(JOBS_BASE_URL + "/statistics/by-workers");

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/by-workers")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobWorkerStatisticsWithMissingFilter() {
    // given
    final var request =
        """
            {
              "page": {
                "limit": 10
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/by-workers")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobWorkerStatisticsWithEmptyFilter() {
    // given
    final var request =
        """
            {
              "filter": {}
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/by-workers")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobWorkerStatisticsWithMissingFrom() {
    // given
    final var request =
        """
            {
              "filter": {
                "to": "2024-07-29T15:51:28.071Z",
                "jobType": "fetch-customer-data"
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/by-workers")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobWorkerStatisticsWithMissingTo() {
    // given
    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:51:28.071Z",
                "jobType": "fetch-customer-data"
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/by-workers")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobWorkerStatisticsWithMissingJobType() {
    // given
    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:51:28.071Z",
                "to": "2024-07-29T15:51:28.071Z"
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/by-workers")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  // -------------------------------------------------------------------------
  // /statistics/time-series tests
  // -------------------------------------------------------------------------

  @Test
  void shouldGetJobTimeSeriesStatistics() {
    // given
    final var time1 = OffsetDateTime.parse("2024-07-29T15:50:00.000Z");
    final var time2 = OffsetDateTime.parse("2024-07-29T15:51:00.000Z");
    final var lastUpdatedAt = OffsetDateTime.parse("2024-07-29T15:51:28.071Z");

    final var entity1 =
        new JobTimeSeriesStatisticsEntity(
            time1,
            new StatusMetric(12, lastUpdatedAt),
            new StatusMetric(10, lastUpdatedAt),
            new StatusMetric(0, null));
    final var entity2 =
        new JobTimeSeriesStatisticsEntity(
            time2,
            new StatusMetric(7, lastUpdatedAt),
            new StatusMetric(6, lastUpdatedAt),
            new StatusMetric(1, lastUpdatedAt));

    final var searchResult =
        new SearchQueryResult.Builder<JobTimeSeriesStatisticsEntity>()
            .total(2L, true)
            .items(List.of(entity1, entity2))
            .endCursor("endCursor")
            .build();

    when(jobServices.getJobTimeSeriesStatistics(any(), any())).thenReturn(searchResult);

    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:51:28.071Z",
                "to": "2024-07-29T15:51:28.071Z",
                "jobType": "fetch-customer-data"
              }
            }""";

    final var expectedResponse =
        """
            {
              "items": [
                {
                  "time": "2024-07-29T15:50:00.000Z",
                  "created":   { "count": 12, "lastUpdatedAt": "2024-07-29T15:51:28.071Z" },
                  "completed": { "count": 10, "lastUpdatedAt": "2024-07-29T15:51:28.071Z" },
                  "failed":    { "count": 0,  "lastUpdatedAt": null }
                },
                {
                  "time": "2024-07-29T15:51:00.000Z",
                  "created":   { "count": 7, "lastUpdatedAt": "2024-07-29T15:51:28.071Z" },
                  "completed": { "count": 6, "lastUpdatedAt": "2024-07-29T15:51:28.071Z" },
                  "failed":    { "count": 1, "lastUpdatedAt": "2024-07-29T15:51:28.071Z" }
                }
              ],
              "page": {
                "totalItems": 2,
                "startCursor": null,
                "endCursor": "endCursor",
                "hasMoreTotalItems": true
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/time-series")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  void shouldGetJobTimeSeriesStatisticsWithResolution() {
    // given
    final var time1 = OffsetDateTime.parse("2024-07-29T15:00:00.000Z");
    final var lastUpdatedAt = OffsetDateTime.parse("2024-07-29T15:51:28.071Z");
    final var entity =
        new JobTimeSeriesStatisticsEntity(
            time1,
            new StatusMetric(100, lastUpdatedAt),
            new StatusMetric(95, lastUpdatedAt),
            new StatusMetric(2, lastUpdatedAt));
    final var searchResult =
        new SearchQueryResult.Builder<JobTimeSeriesStatisticsEntity>()
            .total(1L)
            .items(List.of(entity))
            .build();

    when(jobServices.getJobTimeSeriesStatistics(any(), any())).thenReturn(searchResult);

    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:00:00.000Z",
                "to":   "2024-07-29T15:00:00.000Z",
                "jobType": "fetch-customer-data",
                "resolution": "PT1H"
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/time-series")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.items[0].time")
        .isEqualTo("2024-07-29T15:00:00.000Z")
        .jsonPath("$.items[0].created.count")
        .isEqualTo(100);
  }

  @Test
  void shouldRejectJobTimeSeriesStatisticsWithMissingBody() {
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
            .formatted(JOBS_BASE_URL + "/statistics/time-series");

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/time-series")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobTimeSeriesStatisticsWithMissingFilter() {
    // given
    final var request = "{}";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/time-series")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobTimeSeriesStatisticsWithEmptyFilter() {
    // given
    final var request =
        """
        { "filter": {} }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/time-series")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobTimeSeriesStatisticsWithMissingFrom() {
    // given
    final var request =
        """
            {
              "filter": {
                "to": "2024-07-29T15:51:28.071Z",
                "jobType": "fetch-customer-data"
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/time-series")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobTimeSeriesStatisticsWithMissingTo() {
    // given
    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:51:28.071Z",
                "jobType": "fetch-customer-data"
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/time-series")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobTimeSeriesStatisticsWithMissingJobType() {
    // given
    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:51:28.071Z",
                "to": "2024-07-29T15:51:28.071Z"
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/time-series")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobTimeSeriesStatisticsWithInvalidResolution() {
    // given
    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:51:28.071Z",
                "to":   "2024-07-29T15:51:28.071Z",
                "jobType": "fetch-customer-data",
                "resolution": "not-a-duration"
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/time-series")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  // -------------------------------------------------------------------------
  // /statistics/errors tests
  // -------------------------------------------------------------------------

  @Test
  void shouldGetJobErrorStatistics() {
    // given
    final var entity1 =
        new JobErrorStatisticsEntity("UNHANDLED_ERROR_EVENT", "An unexpected error occurred.", 15);
    final var entity2 =
        new JobErrorStatisticsEntity("IO_ERROR", "Failed to read from remote server.", 3);

    final var searchResult =
        new SearchQueryResult.Builder<JobErrorStatisticsEntity>()
            .total(2L, true)
            .items(List.of(entity1, entity2))
            .endCursor("endCursor")
            .build();

    when(jobServices.getJobErrorStatistics(any(), any())).thenReturn(searchResult);

    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:51:28.071Z",
                "to": "2024-07-29T15:51:28.071Z",
                "jobType": "fetch-customer-data"
              }
            }""";

    final var expectedResponse =
        """
            {
              "items": [
                {
                  "errorCode": "UNHANDLED_ERROR_EVENT",
                  "errorMessage": "An unexpected error occurred.",
                  "workers": 15
                },
                {
                  "errorCode": "IO_ERROR",
                  "errorMessage": "Failed to read from remote server.",
                  "workers": 3
                }
              ],
              "page": {
                "totalItems": 2,
                "startCursor": null,
                "endCursor": "endCursor",
                "hasMoreTotalItems": true
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/errors")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  void shouldGetJobErrorStatisticsWithOptionalFilters() {
    // given
    final var entity =
        new JobErrorStatisticsEntity("UNHANDLED_ERROR_EVENT", "An unexpected error.", 5);

    final var searchResult =
        new SearchQueryResult.Builder<JobErrorStatisticsEntity>()
            .total(1L)
            .items(List.of(entity))
            .build();

    when(jobServices.getJobErrorStatistics(any(), any())).thenReturn(searchResult);

    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:51:28.071Z",
                "to": "2024-07-29T15:51:28.071Z",
                "jobType": "fetch-customer-data",
                "errorCode": { "$like": "UNHANDLED_*" },
                "errorMessage": { "$like": "unexpected*" }
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/errors")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.items[0].errorCode")
        .isEqualTo("UNHANDLED_ERROR_EVENT")
        .jsonPath("$.items[0].workers")
        .isEqualTo(5);
  }

  @Test
  void shouldRejectJobErrorStatisticsWithMissingBody() {
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
            .formatted(JOBS_BASE_URL + "/statistics/errors");

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/errors")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobErrorStatisticsWithMissingFilter() {
    // given
    final var request = "{}";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/errors")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobErrorStatisticsWithMissingFrom() {
    // given
    final var request =
        """
            {
              "filter": {
                "to": "2024-07-29T15:51:28.071Z",
                "jobType": "fetch-customer-data"
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/errors")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobErrorStatisticsWithMissingTo() {
    // given
    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:51:28.071Z",
                "jobType": "fetch-customer-data"
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/errors")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  @Test
  void shouldRejectJobErrorStatisticsWithMissingJobType() {
    // given
    final var request =
        """
            {
              "filter": {
                "from": "2024-07-28T15:51:28.071Z",
                "to": "2024-07-29T15:51:28.071Z"
              }
            }""";

    // when/then
    webClient
        .post()
        .uri(JOBS_BASE_URL + "/statistics/errors")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest();

    verifyNoInteractions(jobServices);
  }

  static Stream<Arguments> jobMetricsDisabledEndpoints() {
    return Stream.of(
        Arguments.of(
            "/statistics/global?from=2024-07-28T15:51:28.071Z&to=2024-07-29T15:51:28.071Z",
            "GET",
            null,
            "/v2/jobs/statistics/global"),
        Arguments.of(
            "/statistics/by-types",
            "POST",
            """
                {
                  "filter": {
                    "from": "2024-07-28T15:51:28.071Z",
                    "to": "2024-07-29T15:51:28.071Z"
                  }
                }""",
            "/v2/jobs/statistics/by-types"),
        Arguments.of(
            "/statistics/by-workers",
            "POST",
            """
                {
                  "filter": {
                    "from": "2024-07-28T15:51:28.071Z",
                    "to": "2024-07-29T15:51:28.071Z",
                    "jobType": "fetch-customer-data"
                  }
                }""",
            "/v2/jobs/statistics/by-workers"),
        Arguments.of(
            "/statistics/time-series",
            "POST",
            """
                {
                  "filter": {
                    "from": "2024-07-28T15:51:28.071Z",
                    "to": "2024-07-29T15:51:28.071Z",
                    "jobType": "fetch-customer-data"
                  }
                }""",
            "/v2/jobs/statistics/time-series"));
  }

  @ParameterizedTest(name = "shouldReturn403For {0} statistics when job metrics disabled")
  @MethodSource("jobMetricsDisabledEndpoints")
  void shouldReturn403WhenJobMetricsDisabled(
      final String uriSuffix,
      final String httpMethod,
      final String requestBody,
      final String expectedInstance) {
    // given
    final var disabledCfg = new GatewayRestConfiguration.JobMetricsConfiguration();
    disabledCfg.setEnabled(false);
    when(gatewayRestConfiguration.getJobMetrics()).thenReturn(disabledCfg);

    // when/then
    final var requestSpec =
        "GET".equals(httpMethod)
            ? webClient.get().uri(JOBS_BASE_URL + uriSuffix).accept(MediaType.APPLICATION_JSON)
            : webClient
                .post()
                .uri(JOBS_BASE_URL + uriSuffix)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody);

    requestSpec
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "title": "FORBIDDEN",
              "status": 403,
              "detail": "Job metrics feature is disabled",
              "instance": "%s"
            }
            """
                .formatted(expectedInstance),
            JsonCompareMode.STRICT);

    verifyNoInteractions(jobServices);
  }
}
