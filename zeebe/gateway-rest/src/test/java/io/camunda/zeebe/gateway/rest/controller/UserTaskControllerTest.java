/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.UserTaskServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

  @MockitoBean UserTaskServices userTaskServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  static Stream<String> urls() {
    return Stream.of("/v1/user-tasks", "/v2/user-tasks");
  }

  static Stream<Pair<RejectionType, String>> invalidArgumentAndUrls() {
    return urls()
        .flatMap(
            url ->
                Stream.of(RejectionType.INVALID_ARGUMENT).flatMap(r -> Stream.of(Pair.of(r, url))));
  }

  static Stream<Pair<RejectionType, String>> alreadyExistsAndUrls() {
    return urls()
        .flatMap(
            url ->
                Stream.of(RejectionType.ALREADY_EXISTS).flatMap(r -> Stream.of(Pair.of(r, url))));
  }

  static Stream<Pair<String, String>> validDateInputsAndUrls() {
    return urls()
        .flatMap(
            url ->
                Stream.of(
                        "2023-11-11T10:10:10.1010Z",
                        "2023-11-11T10:10:10Z",
                        "2023-11-11T10:10:10.1010+01:00",
                        "2023-11-11T10:10:10.101010101+01:00")
                    .flatMap(date -> Stream.of(Pair.of(date, url))));
  }

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    Mockito.when(userTaskServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(userTaskServices);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldCompleteTaskWithoutActionAndVariables(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(BROKER_RESPONSE);
    // when / then
    webClient
        .post()
        .uri(baseUrl + "/2251799813685732/completion")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices).completeUserTask(2251799813685732L, Map.of(), "");
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldCompleteTaskWithAction(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "action": "customAction"
            }""";

    // when / then
    webClient
        .post()
        .uri(baseUrl + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices).completeUserTask(1L, Map.of(), "customAction");
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldCompleteTaskWithVariables(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
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
        .uri(baseUrl + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices).completeUserTask(1L, Map.of("foo", "bar", "baz", 1234), "");
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldCompleteTaskWithActionAndVariables(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
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
        .uri(baseUrl + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .completeUserTask(1L, Map.of("foo", "bar", "baz", 1234), "customAction");
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldUpdateTaskWithAction(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.updateUserTask(anyLong(), any(), anyString()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "action": "customAction"
            }""";

    // when / then
    webClient
        .patch()
        .uri(baseUrl + "/1")
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
        .updateUserTask(eq(1L), argumentCaptor.capture(), eq("customAction"));
    Assertions.assertThat(argumentCaptor.getValue())
        .hasNoChangedAttributes()
        .hasDueDate("")
        .hasFollowUpDate("")
        .hasNoCandidateGroupsList()
        .hasNoCandidateUsersList();
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldUpdateTaskWithChanges(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.updateUserTask(anyLong(), any(), anyString()))
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
        .uri(baseUrl + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(UserTaskRecord.class);
    Mockito.verify(userTaskServices).updateUserTask(eq(1L), argumentCaptor.capture(), eq(""));
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
  @MethodSource("urls")
  void shouldUpdateTaskWithPartialChanges(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.updateUserTask(anyLong(), any(), anyString()))
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
        .uri(baseUrl + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(UserTaskRecord.class);
    Mockito.verify(userTaskServices).updateUserTask(eq(1L), argumentCaptor.capture(), eq(""));
    Assertions.assertThat(argumentCaptor.getValue())
        .hasChangedAttributes(UserTaskRecord.CANDIDATE_GROUPS, UserTaskRecord.FOLLOW_UP_DATE)
        .hasDueDate("")
        .hasFollowUpDate(TEST_TIME)
        .hasCandidateGroupsList("foo")
        .hasNoCandidateUsersList();
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldUpdateTaskWithPartialEmptyValueChanges(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.updateUserTask(anyLong(), any(), anyString()))
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
        .uri(baseUrl + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(UserTaskRecord.class);
    Mockito.verify(userTaskServices).updateUserTask(eq(1L), argumentCaptor.capture(), eq(""));
    Assertions.assertThat(argumentCaptor.getValue())
        .hasChangedAttributes(UserTaskRecord.CANDIDATE_GROUPS, UserTaskRecord.FOLLOW_UP_DATE)
        .hasDueDate("")
        .hasFollowUpDate("")
        .hasNoCandidateGroupsList()
        .hasNoCandidateUsersList();
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldUpdateTaskWithActionAndChanges(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.updateUserTask(anyLong(), any(), anyString()))
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
        .uri(baseUrl + "/1")
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
        .updateUserTask(eq(1L), argumentCaptor.capture(), eq("customAction"));
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
  @MethodSource("validDateInputsAndUrls")
  void shouldUpdateTaskWithDateChanges(final Pair<String, String> dateAndUrl) {
    // given
    Mockito.when(userTaskServices.updateUserTask(anyLong(), any(), anyString()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "changeset": {
                "dueDate": "%s",
                "followUpDate": "%s"
              }
            }"""
            .formatted(dateAndUrl.getLeft(), dateAndUrl.getLeft());

    // when / then
    webClient
        .patch()
        .uri(dateAndUrl.getRight() + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(UserTaskRecord.class);
    Mockito.verify(userTaskServices).updateUserTask(eq(1L), argumentCaptor.capture(), eq(""));
    Assertions.assertThat(argumentCaptor.getValue())
        .hasChangedAttributes(UserTaskRecord.DUE_DATE, UserTaskRecord.FOLLOW_UP_DATE)
        .hasDueDate(dateAndUrl.getLeft())
        .hasFollowUpDate(dateAndUrl.getLeft());
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldYieldBadRequestWhenUpdateTaskWithoutActionAndChanges(final String baseUrl) {
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
            .formatted(baseUrl + "/1");

    // when / then
    webClient
        .patch()
        .uri(baseUrl + "/1")
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

  @ParameterizedTest
  @MethodSource("urls")
  void shouldYieldBadRequestWhenUpdateTaskWithoutMalformedDueDate(final String baseUrl) {
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
            .formatted(baseUrl + "/1");

    // when / then
    webClient
        .patch()
        .uri(baseUrl + "/1")
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
  @MethodSource("urls")
  void shouldYieldBadRequestWhenUpdateTaskWithMalformedFollowUpDate(final String baseUrl) {
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
            .formatted(baseUrl + "/1");

    // when / then
    webClient
        .patch()
        .uri(baseUrl + "/1")
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
  @MethodSource("urls")
  void shouldYieldBadRequestWhenUpdateTaskWithInvalidOffsetFollowUpDate(final String baseUrl) {
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
            .formatted(baseUrl + "/1");

    // when / then
    webClient
        .patch()
        .uri(baseUrl + "/1")
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
  @MethodSource("urls")
  void shouldYieldBadRequestWhenUpdateTaskWithoutMalformedFollowUpAndDueDate(final String baseUrl) {
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
            .formatted(baseUrl + "/1");

    // when / then
    webClient
        .patch()
        .uri(baseUrl + "/1")
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
  @MethodSource("urls")
  void shouldYieldBadRequestWhenUpdateTaskWithUntrackedChanges(final String baseUrl) {
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
            .formatted(baseUrl + "/1");

    // when / then
    webClient
        .patch()
        .uri(baseUrl + "/1")
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
  @MethodSource("urls")
  void shouldYieldBadRequestWhenUpdateTaskWithOnlyUnknownProperties(final String baseUrl) {
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
            .formatted(baseUrl + "/1");

    // when / then
    webClient
        .patch()
        .uri(baseUrl + "/1")
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

  static Stream<Arguments> urlsAndPriorityValue() {
    return urls().flatMap(url -> Stream.of(Arguments.of(url, -1), Arguments.of(url, 101)));
  }

  @ParameterizedTest
  @MethodSource("urlsAndPriorityValue")
  void shouldYieldBadRequestWhenUpdateTaskWithPriorityOutOfBounds(
      final String baseUrl, final int priority) {
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
            .formatted(priority, baseUrl + "/1");

    // when / then
    webClient
        .patch()
        .uri(baseUrl + "/1")
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

  @ParameterizedTest
  @MethodSource("urls")
  void shouldYieldNotFoundWhenTaskNotFound(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
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
            .formatted(baseUrl + "/1/completion");

    // when / then
    webClient
        .post()
        .uri(baseUrl + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    Mockito.verify(userTaskServices).completeUserTask(1L, Map.of(), "");
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldYieldConflictWhenInvalidState(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
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
            .formatted(baseUrl + "/1/completion");

    // when / then
    webClient
        .post()
        .uri(baseUrl + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    Mockito.verify(userTaskServices).completeUserTask(1L, Map.of(), "");
  }

  @ParameterizedTest
  @MethodSource("invalidArgumentAndUrls")
  public void shouldYieldBadRequestWhenRejectionOfInput(
      final Pair<RejectionType, String> parameters) {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        UserTaskIntent.COMPLETE, 1L, parameters.getLeft(), "Just an error"))));

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "%s",
              "detail": "Command 'COMPLETE' rejected with code '%s': Just an error",
              "instance": "%s"
            }"""
            .formatted(
                parameters.getLeft().name(),
                parameters.getLeft(),
                parameters.getRight() + "/1/completion");

    // when / then
    webClient
        .post()
        .uri(parameters.getRight() + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    Mockito.verify(userTaskServices).completeUserTask(1L, Map.of(), "");
  }

  @ParameterizedTest
  @MethodSource("alreadyExistsAndUrls")
  public void shouldYieldConflictWhenRejectionOfInput(
      final Pair<RejectionType, String> parameters) {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        UserTaskIntent.COMPLETE, 1L, parameters.getLeft(), "Just an error"))));

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "status": 409,
              "title": "%s",
              "detail": "Command 'COMPLETE' rejected with code '%s': Just an error",
              "instance": "%s"
            }"""
            .formatted(
                parameters.getLeft().name(),
                parameters.getLeft(),
                parameters.getRight() + "/1/completion");

    // when / then
    webClient
        .post()
        .uri(parameters.getRight() + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    Mockito.verify(userTaskServices).completeUserTask(1L, Map.of(), "");
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldAssignTaskWithoutActionAndAllowOverride(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.assignUserTask(anyLong(), anyString(), anyString(), anyBoolean()))
        .thenReturn(BROKER_RESPONSE);
    final var request =
        """
            {
              "assignee": "Test Assignee"
            }""";

    // when / then
    webClient
        .post()
        .uri(baseUrl + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .assignUserTask(2251799813685732L, "Test Assignee", "assign", true);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldAssignTaskWithActionWithoutAllowOverride(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.assignUserTask(anyLong(), anyString(), anyString(), anyBoolean()))
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
        .uri(baseUrl + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .assignUserTask(2251799813685732L, "Test Assignee", "custom action", true);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldAssignTaskWithActionWithAllowOverrideTrue(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.assignUserTask(anyLong(), anyString(), anyString(), anyBoolean()))
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
        .uri(baseUrl + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .assignUserTask(2251799813685732L, "Test Assignee", "custom action", true);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldAssignTaskWithActionWithAllowOverrideFalse(final String baseUrl) {
    // gíven
    Mockito.when(userTaskServices.assignUserTask(anyLong(), anyString(), anyString(), anyBoolean()))
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
        .uri(baseUrl + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .assignUserTask(2251799813685732L, "Test Assignee", "custom action", false);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldAssignTaskWithoutActionWithAllowOverrideTrue(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.assignUserTask(anyLong(), anyString(), anyString(), anyBoolean()))
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
        .uri(baseUrl + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .assignUserTask(2251799813685732L, "Test Assignee", "assign", true);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldAssignTaskWithoutActionWithAllowOverrideFalse(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.assignUserTask(anyLong(), anyString(), anyString(), anyBoolean()))
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
        .uri(baseUrl + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices)
        .assignUserTask(2251799813685732L, "Test Assignee", "assign", false);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldYieldBadRequestWhenNoAssigneeForTaskAssignment(final String baseUrl) {
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
            .formatted(baseUrl + "/1/assignment");

    // when / then
    webClient
        .post()
        .uri(baseUrl + "/1/assignment")
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
  @MethodSource("urls")
  void shouldUnassignTask(final String baseUrl) {
    // given
    Mockito.when(userTaskServices.unassignUserTask(anyLong(), anyString()))
        .thenReturn(BROKER_RESPONSE);
    // when / then
    webClient
        .delete()
        .uri(baseUrl + "/2251799813685732/assignee")
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    Mockito.verify(userTaskServices).unassignUserTask(2251799813685732L, "unassign");
  }
}
