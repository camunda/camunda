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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.UserTaskServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskLookupRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskLookupIntent;
import io.camunda.zeebe.protocol.record.value.UserTaskLifecycleState;
import io.camunda.zeebe.test.util.logging.RecordingAppender;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(UserTaskController.class)
public class UserTaskControllerTest extends RestControllerTest {

  static final CompletableFuture<UserTaskRecord> BROKER_RESPONSE =
      CompletableFuture.completedFuture(new UserTaskRecord());
  static final String TEST_TIME =
      OffsetDateTime.of(2023, 11, 11, 11, 11, 11, 11, ZoneOffset.of("Z")).toString();
  static final String USER_TASKS_V2_URL = "/v2/user-tasks";

  @MockitoBean UserTaskServices userTaskServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean ServiceRegistry serviceRegistry;

  @BeforeEach
  void setupServices() {
    when(serviceRegistry.userTaskServices(any())).thenReturn(userTaskServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldCompleteTaskWithoutActionAndVariables() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString(), any()))
        .thenReturn(BROKER_RESPONSE);
    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/2251799813685732/completion")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .completeUserTask(eq(2251799813685732L), eq(Map.of()), eq(""), any());
  }

  @Test
  void shouldCompleteTaskWithAction() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString(), any()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "action": "customAction"
            }""";

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .completeUserTask(eq(1L), eq(Map.of()), eq("customAction"), any());
  }

  @Test
  void shouldCompleteTaskWithVariables() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString(), any()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "variables" : {
                "foo": "bar",
                "baz": 1234
              }
            }""";

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .completeUserTask(eq(1L), eq(Map.of("foo", "bar", "baz", 1234)), eq(""), any());
  }

  @Test
  void shouldCompleteTaskWithActionAndVariables() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString(), any()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "action": "customAction",
              "variables": {
                "foo": "bar",
                "baz": 1234
              }
            }""";

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .completeUserTask(eq(1L), eq(Map.of("foo", "bar", "baz", 1234)), eq("customAction"), any());
  }

  @Test
  void shouldUpdateTaskWithAction() {
    // given
    Mockito.when(userTaskServices.updateUserTask(anyLong(), any(), anyString(), any()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "action": "customAction"
            }""";

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_V2_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(UserTaskRecord.class);
    Mockito.verify(userTaskServices)
        .updateUserTask(eq(1L), argumentCaptor.capture(), eq("customAction"), any());
    Assertions.assertThat(argumentCaptor.getValue())
        .hasNoChangedAttributes()
        .hasDueDate("")
        .hasFollowUpDate("")
        .hasNoCandidateGroupsList()
        .hasNoCandidateUsersList();
  }

  @Test
  void shouldUpdateTaskWithChanges() {
    // given
    Mockito.when(userTaskServices.updateUserTask(anyLong(), any(), anyString(), any()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "changeset": {
                "candidateGroups": ["foo"],
                "candidateUsers": ["bar"],
                "dueDate": "%s",
                "followUpDate": "%s",
                "priority": 33
              }
            }"""
            .formatted(TEST_TIME, TEST_TIME);

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_V2_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(UserTaskRecord.class);
    Mockito.verify(userTaskServices)
        .updateUserTask(eq(1L), argumentCaptor.capture(), eq(""), any());
    Assertions.assertThat(argumentCaptor.getValue())
        .hasChangedAttributes(
            UserTaskRecord.CANDIDATE_USERS,
            UserTaskRecord.CANDIDATE_GROUPS,
            UserTaskRecord.DUE_DATE,
            UserTaskRecord.FOLLOW_UP_DATE,
            UserTaskRecord.PRIORITY)
        .hasDueDate(TEST_TIME)
        .hasFollowUpDate(TEST_TIME)
        .hasCandidateGroupsList("foo")
        .hasCandidateUsersList("bar")
        .hasPriority(33);
  }

  @Test
  void shouldUpdateTaskWithPartialChanges() {
    // given
    Mockito.when(userTaskServices.updateUserTask(anyLong(), any(), anyString(), any()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "changeset": {
                "candidateGroups": ["foo"],
                "followUpDate": "%s"
              }
            }"""
            .formatted(TEST_TIME);

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_V2_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(UserTaskRecord.class);
    Mockito.verify(userTaskServices)
        .updateUserTask(eq(1L), argumentCaptor.capture(), eq(""), any());
    Assertions.assertThat(argumentCaptor.getValue())
        .hasChangedAttributes(UserTaskRecord.CANDIDATE_GROUPS, UserTaskRecord.FOLLOW_UP_DATE)
        .hasDueDate("")
        .hasFollowUpDate(TEST_TIME)
        .hasCandidateGroupsList("foo")
        .hasNoCandidateUsersList();
  }

  @Test
  void shouldUpdateTaskWithPartialEmptyValueChanges() {
    // given
    Mockito.when(userTaskServices.updateUserTask(anyLong(), any(), anyString(), any()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "changeset": {
                "candidateGroups": [],
                "followUpDate": ""
              }
            }""";

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_V2_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(UserTaskRecord.class);
    Mockito.verify(userTaskServices)
        .updateUserTask(eq(1L), argumentCaptor.capture(), eq(""), any());
    Assertions.assertThat(argumentCaptor.getValue())
        .hasChangedAttributes(UserTaskRecord.CANDIDATE_GROUPS, UserTaskRecord.FOLLOW_UP_DATE)
        .hasDueDate("")
        .hasFollowUpDate("")
        .hasNoCandidateGroupsList()
        .hasNoCandidateUsersList();
  }

  @Test
  void shouldUpdateTaskWithActionAndChanges() {
    // given
    Mockito.when(userTaskServices.updateUserTask(anyLong(), any(), anyString(), any()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "action": "customAction",
              "changeset": {
                "candidateGroups": ["foo"],
                "candidateUsers": ["bar"],
                "dueDate": "%s",
                "followUpDate": "%s",
                "priority": 33
              }
            }"""
            .formatted(TEST_TIME, TEST_TIME);

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_V2_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(UserTaskRecord.class);
    Mockito.verify(userTaskServices)
        .updateUserTask(eq(1L), argumentCaptor.capture(), eq("customAction"), any());
    Assertions.assertThat(argumentCaptor.getValue())
        .hasChangedAttributes(
            UserTaskRecord.CANDIDATE_USERS,
            UserTaskRecord.CANDIDATE_GROUPS,
            UserTaskRecord.DUE_DATE,
            UserTaskRecord.FOLLOW_UP_DATE,
            UserTaskRecord.PRIORITY)
        .hasDueDate(TEST_TIME)
        .hasFollowUpDate(TEST_TIME)
        .hasCandidateGroupsList("foo")
        .hasCandidateUsersList("bar")
        .hasPriority(33);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2023-11-11T10:10:10.1010Z",
        "2023-11-11T10:10:10Z",
        "2023-11-11T10:10:10.1010+01:00",
        "2023-11-11T10:10:10.101010101+01:00"
      })
  void shouldUpdateTaskWithDateChanges(final String date) {
    // given
    Mockito.when(userTaskServices.updateUserTask(anyLong(), any(), anyString(), any()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "changeset": {
                "dueDate": "%s",
                "followUpDate": "%s"
              }
            }"""
            .formatted(date, date);

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_V2_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(UserTaskRecord.class);
    Mockito.verify(userTaskServices)
        .updateUserTask(eq(1L), argumentCaptor.capture(), eq(""), any());
    Assertions.assertThat(argumentCaptor.getValue())
        .hasChangedAttributes(UserTaskRecord.DUE_DATE, UserTaskRecord.FOLLOW_UP_DATE)
        .hasDueDate(date)
        .hasFollowUpDate(date);
  }

  @Test
  void shouldYieldBadRequestWhenUpdateTaskWithoutActionAndChanges() {
    // given
    final var expectedBody =
        """
                    {
                      "type": "about:blank",
                      "status": 400,
                      "title": "INVALID_ARGUMENT",
                      "detail": "No update data provided. Provide at least an \\"action\\" or a non-null value \
            for a supported attribute in the \\"changeset\\".",
                      "instance": "%s"
                    }"""
            .formatted(USER_TASKS_V2_URL + "/1");

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_V2_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    Mockito.verifyNoInteractions(userTaskServices);
  }

  @Test
  void shouldYieldBadRequestWhenUpdateTaskWithoutMalformedDueDate() {
    // given
    final var request =
        """
            {
              "changeset": {
                "dueDate": "foo"
              }
            }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided due date 'foo' cannot be parsed as a date according to RFC 3339, section 5.6.",
              "instance": "%s"
            }"""
            .formatted(USER_TASKS_V2_URL + "/1");

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_V2_URL + "/1")
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

    Mockito.verifyNoInteractions(userTaskServices);
  }

  @Test
  void shouldYieldBadRequestWhenUpdateTaskWithMalformedFollowUpDate() {
    // given
    final var request =
        """
            {
              "changeset": {
                "followUpDate": "foo"
              }
            }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided follow-up date 'foo' cannot be parsed as a date according to RFC 3339, section 5.6.",
              "instance": "%s"
            }"""
            .formatted(USER_TASKS_V2_URL + "/1");

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_V2_URL + "/1")
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

    Mockito.verifyNoInteractions(userTaskServices);
  }

  @Test
  void shouldYieldBadRequestWhenUpdateTaskWithInvalidOffsetFollowUpDate() {
    // given
    final var request =
        """
            {
              "changeset": {
                "followUpDate": "2023-11-11T12:12:12.1234+0100",
                "dueDate": "2023-11-11T10:10:10.1010+01:00[Europe/Paris]"
              }
            }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The provided due date '2023-11-11T10:10:10.1010+01:00[Europe/Paris]' \
            cannot be parsed as a date according to RFC 3339, section 5.6. The provided follow-up \
            date '2023-11-11T12:12:12.1234+0100' cannot be parsed as a date according to RFC 3339, \
            section 5.6.",
              "instance": "%s"
            }"""
            .formatted(USER_TASKS_V2_URL + "/1");

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_V2_URL + "/1")
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

    Mockito.verifyNoInteractions(userTaskServices);
  }

  @Test
  void shouldYieldBadRequestWhenUpdateTaskWithoutMalformedFollowUpAndDueDate() {
    // given
    final var request =
        """
            {
              "changeset": {
                "dueDate": "bar",
                "followUpDate": "foo"
              }
            }""";

    final var expectedBody =
        """
                        {
                          "type": "about:blank",
                          "status": 400,
                          "title": "INVALID_ARGUMENT",
                          "detail": "The provided due date 'bar' cannot be parsed as a date according to \
            RFC 3339, section 5.6. The provided follow-up date 'foo' cannot be parsed as a date according to \
            RFC 3339, section 5.6.",
                          "instance": "%s"
                        }"""
            .formatted(USER_TASKS_V2_URL + "/1");

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_V2_URL + "/1")
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

    Mockito.verifyNoInteractions(userTaskServices);
  }

  @Test
  void shouldYieldBadRequestWhenUpdateTaskWithUntrackedChanges() {
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
              "detail": "No update data provided. Provide at least an \\"action\\" or a non-null value for a supported attribute in the \\"changeset\\".",
              "instance": "%s"
            }"""
            .formatted(USER_TASKS_V2_URL + "/1");

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_V2_URL + "/1")
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

    Mockito.verifyNoInteractions(userTaskServices);
  }

  @Test
  void shouldYieldBadRequestWhenUpdateTaskWithOnlyUnknownProperties() {
    // given
    final var request =
        """
            {
              "changeset": {
                "elementInstanceKey": 12345
              }
            }""";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No update data provided. Provide at least an \\"action\\" or a non-null value for a supported attribute in the \\"changeset\\".",
              "instance": "%s"
            }"""
            .formatted(USER_TASKS_V2_URL + "/1");

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_V2_URL + "/1")
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

    Mockito.verifyNoInteractions(userTaskServices);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 101})
  void shouldYieldBadRequestWhenUpdateTaskWithPriorityOutOfBounds(final int priority) {
    // given
    final var request =
        """
            {
              "changeset": {
                "priority": %d
              }
            }"""
            .formatted(priority);

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "The value for priority is '%s' but must be within the [0,100] range.",
              "instance": "%s"
            }"""
            .formatted(priority, USER_TASKS_V2_URL + "/1");

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_V2_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    Mockito.verifyNoInteractions(userTaskServices);
  }

  @Test
  void shouldYieldNotFoundWhenTaskNotFound() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        UserTaskIntent.COMPLETE, 1L, RejectionType.NOT_FOUND, "Task not found"))));

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "status": 404,
              "title": "NOT_FOUND",
              "detail": "Command 'COMPLETE' rejected with code 'NOT_FOUND': Task not found",
              "instance": "%s"
            }"""
            .formatted(USER_TASKS_V2_URL + "/1/completion");

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    Mockito.verify(userTaskServices).completeUserTask(eq(1L), eq(Map.of()), eq(""), any());
  }

  @Test
  void shouldReturnGatewayTimeoutWhenCompleteTaskTimesOut() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(new TimeoutException("Task listener blocked completion"))));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("DEADLINE_EXCEEDED")
        .jsonPath("$.detail")
        .isEqualTo("Expected to handle request, but request timed out between gateway and broker")
        .jsonPath("$.status")
        .isEqualTo(504);
  }

  @Test
  void shouldYieldConflictWhenInvalidState() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        UserTaskIntent.COMPLETE,
                        1L,
                        RejectionType.INVALID_STATE,
                        "Task is not in state CREATED"))));

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "status": 409,
              "title": "INVALID_STATE",
              "detail": "Command 'COMPLETE' rejected with code 'INVALID_STATE': Task is not in state CREATED",
              "instance": "%s"
            }"""
            .formatted(USER_TASKS_V2_URL + "/1/completion");

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    Mockito.verify(userTaskServices).completeUserTask(eq(1L), eq(Map.of()), eq(""), any());
  }

  @Test
  public void shouldYieldBadRequestWhenRejectionOfInput() {
    // given
    final var rejectionType = RejectionType.INVALID_ARGUMENT;
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        UserTaskIntent.COMPLETE, 1L, rejectionType, "Just an error"))));

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "%s",
              "detail": "Command 'COMPLETE' rejected with code '%s': Just an error",
              "instance": "%s"
            }"""
            .formatted(rejectionType.name(), rejectionType, USER_TASKS_V2_URL + "/1/completion");

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    Mockito.verify(userTaskServices).completeUserTask(eq(1L), eq(Map.of()), eq(""), any());
  }

  @Test
  public void shouldYieldConflictWhenRejectionOfInput() {
    // given
    final var rejectionType = RejectionType.ALREADY_EXISTS;
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        UserTaskIntent.COMPLETE, 1L, rejectionType, "Just an error"))));

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "status": 409,
              "title": "%s",
              "detail": "Command 'COMPLETE' rejected with code '%s': Just an error",
              "instance": "%s"
            }"""
            .formatted(rejectionType.name(), rejectionType, USER_TASKS_V2_URL + "/1/completion");

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    Mockito.verify(userTaskServices).completeUserTask(eq(1L), eq(Map.of()), eq(""), any());
  }

  @Test
  void shouldAssignTaskWithoutActionAndAllowOverride() {
    // given
    Mockito.when(
            userTaskServices.assignUserTask(
                anyLong(), anyString(), anyString(), anyBoolean(), any()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "assignee": "Test Assignee"
            }""";

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .assignUserTask(eq(2251799813685732L), eq("Test Assignee"), eq("assign"), eq(true), any());
  }

  @Test
  void shouldAssignTaskWithActionWithoutAllowOverride() {
    // given
    Mockito.when(
            userTaskServices.assignUserTask(
                anyLong(), anyString(), anyString(), anyBoolean(), any()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "assignee": "Test Assignee",
              "action": "custom action"
            }""";

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .assignUserTask(
            eq(2251799813685732L), eq("Test Assignee"), eq("custom action"), eq(true), any());
  }

  @Test
  void shouldAssignTaskWithActionWithAllowOverrideTrue() {
    // given
    Mockito.when(
            userTaskServices.assignUserTask(
                anyLong(), anyString(), anyString(), anyBoolean(), any()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "assignee": "Test Assignee",
              "action": "custom action",
              "allowOverride": true
            }""";

    // when/then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .assignUserTask(
            eq(2251799813685732L), eq("Test Assignee"), eq("custom action"), eq(true), any());
  }

  @Test
  void shouldAssignTaskWithActionWithAllowOverrideFalse() {
    // gíven
    Mockito.when(
            userTaskServices.assignUserTask(
                anyLong(), anyString(), anyString(), anyBoolean(), any()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "assignee": "Test Assignee",
              "action": "custom action",
              "allowOverride": false
            }""";

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .assignUserTask(
            eq(2251799813685732L), eq("Test Assignee"), eq("custom action"), eq(false), any());
  }

  @Test
  void shouldAssignTaskWithoutActionWithAllowOverrideTrue() {
    // given
    Mockito.when(
            userTaskServices.assignUserTask(
                anyLong(), anyString(), anyString(), anyBoolean(), any()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "assignee": "Test Assignee",
              "allowOverride": true
            }""";

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .assignUserTask(eq(2251799813685732L), eq("Test Assignee"), eq("assign"), eq(true), any());
  }

  @Test
  void shouldAssignTaskWithoutActionWithAllowOverrideFalse() {
    // given
    Mockito.when(
            userTaskServices.assignUserTask(
                anyLong(), anyString(), anyString(), anyBoolean(), any()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "assignee": "Test Assignee",
              "allowOverride": false
            }""";

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .assignUserTask(eq(2251799813685732L), eq("Test Assignee"), eq("assign"), eq(false), any());
  }

  @Test
  void shouldYieldBadRequestWhenNoAssigneeForTaskAssignment() {
    // given
    final var request = "{}";

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "INVALID_ARGUMENT",
              "detail": "No assignee provided",
              "instance": "%s"
            }"""
            .formatted(USER_TASKS_V2_URL + "/1/assignment");

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/1/assignment")
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

    Mockito.verifyNoInteractions(userTaskServices);
  }

  @Test
  void shouldReturnGatewayTimeoutWhenAssignTaskTimesOut() {
    // given
    Mockito.when(
            userTaskServices.assignUserTask(
                anyLong(), anyString(), anyString(), anyBoolean(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(new TimeoutException("Task listener blocked assignment"))));

    final var request =
        """
            {
              "assignee": "Test Assignee"
            }""";

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/1/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("DEADLINE_EXCEEDED")
        .jsonPath("$.detail")
        .isEqualTo("Expected to handle request, but request timed out between gateway and broker")
        .jsonPath("$.status")
        .isEqualTo(504);
  }

  @Test
  void shouldReturnGatewayTimeoutWhenUpdateTaskTimesOut() {
    // given
    Mockito.when(userTaskServices.updateUserTask(anyLong(), any(), anyString(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(new TimeoutException("Task listener blocked update"))));

    final var request =
        """
            {
              "changeset": {
                "priority": 50
              }
            }""";

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_V2_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("DEADLINE_EXCEEDED")
        .jsonPath("$.detail")
        .isEqualTo("Expected to handle request, but request timed out between gateway and broker")
        .jsonPath("$.status")
        .isEqualTo(504);
  }

  @Test
  void shouldUnassignTask() {
    // given
    Mockito.when(userTaskServices.unassignUserTask(anyLong(), anyString(), any()))
        .thenReturn(BROKER_RESPONSE);
    // when / then
    webClient
        .delete()
        .uri(USER_TASKS_V2_URL + "/2251799813685732/assignee")
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices).unassignUserTask(eq(2251799813685732L), eq("unassign"), any());
  }

  @Test
  void shouldReturnGatewayTimeoutWhenUnassignTaskTimesOut() {
    // given
    Mockito.when(userTaskServices.unassignUserTask(anyLong(), anyString(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(new TimeoutException("Task listener blocked unassignment"))));

    // when / then
    webClient
        .delete()
        .uri(USER_TASKS_V2_URL + "/1/assignee")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("DEADLINE_EXCEEDED")
        .jsonPath("$.detail")
        .isEqualTo("Expected to handle request, but request timed out between gateway and broker")
        .jsonPath("$.status")
        .isEqualTo(504);
  }

  @Test
  void shouldLookUpUserTask() {
    // given
    final var lookup =
        new UserTaskLookupRecord()
            .setLifecycleState(UserTaskLifecycleState.ASSIGNING)
            .setUserTask(
                new UserTaskRecord()
                    .setUserTaskKey(1L)
                    .setElementId("underwriter-review")
                    .setElementInstanceKey(2L)
                    .setProcessInstanceKey(3L)
                    .setRootProcessInstanceKey(3L)
                    .setBpmnProcessId("loan")
                    .setProcessDefinitionKey(4L)
                    .setProcessDefinitionVersion(1)
                    .setAssignee("previous-assignee")
                    .setCandidateGroupsList(List.of("underwriters"))
                    .setCreationTimestamp(Instant.parse("2026-11-02T10:00:00Z").toEpochMilli())
                    .setVariables(
                        new UnsafeBuffer(
                            MsgPackConverter.convertToMsgPack(
                                Map.of("recommendation", "recommended")))));
    when(userTaskServices.lookupUserTask(anyLong(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(lookup));

    final var expectedBody =
        """
            {
              "userTaskKey": "1",
              "state": "ASSIGNING",
              "elementId": "underwriter-review",
              "elementInstanceKey": "2",
              "processInstanceKey": "3",
              "rootProcessInstanceKey": "3",
              "processDefinitionId": "loan",
              "processDefinitionKey": "4",
              "processDefinitionVersion": 1,
              "tenantId": "<default>",
              "assignee": "previous-assignee",
              "candidateGroups": ["underwriters"],
              "candidateUsers": [],
              "dueDate": null,
              "followUpDate": null,
              "formKey": null,
              "externalFormReference": null,
              "customHeaders": {},
              "priority": 50,
              "tags": [],
              "businessId": null,
              "creationDate": "2026-11-02T10:00:00.000Z",
              "variables": { "recommendation": "recommended" }
            }""";

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/1/lookup")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldLookUpAllVariablesWithoutRequestBody() {
    // given
    when(userTaskServices.lookupUserTask(anyLong(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(createdLookup()));

    // when
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/1/lookup")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk();

    // then
    Mockito.verify(userTaskServices).lookupUserTask(eq(1L), eq(List.of()), any());
  }

  @Test
  void shouldLookUpRequestedVariables() {
    // given
    when(userTaskServices.lookupUserTask(anyLong(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(createdLookup()));

    // when
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/1/lookup")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            { "fetchVariables": ["recommendation", "applicant"] }""")
        .exchange()
        .expectStatus()
        .isOk();

    // then
    Mockito.verify(userTaskServices)
        .lookupUserTask(eq(1L), eq(List.of("recommendation", "applicant")), any());
  }

  @ParameterizedTest
  @MethodSource("lookupRejections")
  void shouldMapLookupRejectionToProblemDetail(
      final RejectionType rejectionType, final HttpStatus expectedStatus) {
    // given
    when(userTaskServices.lookupUserTask(anyLong(), any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        UserTaskLookupIntent.FETCH, 1L, rejectionType, "rejection reason"))));

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "status": %d,
              "title": "%s",
              "detail": "Command 'FETCH' rejected with code '%s': rejection reason",
              "instance": "%s"
            }"""
            .formatted(
                expectedStatus.value(),
                rejectionType,
                rejectionType,
                USER_TASKS_V2_URL + "/1/lookup");

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/1/lookup")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(expectedStatus)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  void shouldYieldServiceUnavailableWhenLookupIsRejectedByBackpressure() {
    // given
    when(userTaskServices.lookupUserTask(anyLong(), any(), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_V2_URL + "/1/lookup")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("RESOURCE_EXHAUSTED");
  }

  @Test
  void shouldYieldInternalServerErrorAndLogWhenLookupHasNoLifecycleState() {
    // given
    final var recorder = new RecordingAppender();
    final var logger = (Logger) LogManager.getLogger(GatewayErrorMapper.class);
    recorder.start();
    logger.addAppender(recorder);
    when(userTaskServices.lookupUserTask(anyLong(), any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new UserTaskLookupRecord().setUserTask(new UserTaskRecord().setUserTaskKey(1L))));

    try {
      // when
      webClient
          .post()
          .uri(USER_TASKS_V2_URL + "/1/lookup")
          .accept(MediaType.APPLICATION_JSON)
          .exchange()
          // then
          .expectStatus()
          .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
          .expectHeader()
          .contentType(MediaType.APPLICATION_PROBLEM_JSON)
          .expectBody()
          .jsonPath("$.status")
          .isEqualTo(500)
          .jsonPath("$.state")
          .doesNotExist();

      assertThat(recorder.getAppendedEvents())
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getThrown()).hasMessageContaining("UNSPECIFIED");
              });
    } finally {
      logger.removeAppender(recorder);
      recorder.stop();
    }
  }

  static Stream<Arguments> lookupRejections() {
    return Stream.of(
        Arguments.of(RejectionType.NOT_FOUND, HttpStatus.NOT_FOUND),
        Arguments.of(RejectionType.INVALID_STATE, HttpStatus.CONFLICT),
        Arguments.of(RejectionType.FORBIDDEN, HttpStatus.FORBIDDEN));
  }

  private static UserTaskLookupRecord createdLookup() {
    return new UserTaskLookupRecord()
        .setLifecycleState(UserTaskLifecycleState.CREATED)
        .setUserTask(new UserTaskRecord().setUserTaskKey(1L));
  }
}
