/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejectionResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserTaskCompletionRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserTaskUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;

@WebMvcTest(UserTaskController.class)
public class UserTaskControllerTest extends RestControllerTest {

  static final BrokerResponse<UserTaskRecord> BROKER_RESPONSE =
      new BrokerResponse<>(new UserTaskRecord());
  static final String TEST_TIME =
      ZonedDateTime.of(2023, 11, 11, 11, 11, 11, 11, ZoneId.of("UTC")).toString();
  @SpyBean StubbedBrokerClient brokerClient;

  static Stream<String> urls() {
    return Stream.of("/v1/user-tasks", "/v2/user-tasks");
  }

  static Stream<Pair<RejectionType, String>> rejectionsAndUrls() {
    return urls()
        .flatMap(
            url ->
                Stream.of(RejectionType.INVALID_ARGUMENT, RejectionType.ALREADY_EXISTS)
                    .flatMap(r -> Stream.of(Pair.of(r, url))));
  }

  static Stream<Pair<RejectionType, String>> exceptionsAndUrls() {
    return urls()
        .flatMap(
            url ->
                Stream.of(
                        RejectionType.PROCESSING_ERROR,
                        RejectionType.EXCEEDED_BATCH_RECORD_SIZE,
                        RejectionType.SBE_UNKNOWN,
                        RejectionType.NULL_VAL)
                    .flatMap(r -> Stream.of(Pair.of(r, url))));
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldCompleteTaskWithoutActionAndVariables(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(BrokerUserTaskCompletionRequest.class, request -> BROKER_RESPONSE);
    // when / then
    webClient
        .perform(
            asyncRequest(
                post(baseUrl + "/2251799813685732/completion").accept(MediaType.APPLICATION_JSON)))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskCompletionRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("")
        .hasVariables(Collections.emptyMap());
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldCompleteTaskWithAction(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(BrokerUserTaskCompletionRequest.class, request -> BROKER_RESPONSE);
    final var request =
        """
        {
          "action": "customAction"
        }""";

    // when / then
    webClient
        .perform(
            asyncRequest(
                post(baseUrl + "/1/completion")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskCompletionRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("customAction")
        .hasVariables(Collections.emptyMap());
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldCompleteTaskWithVariables(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(BrokerUserTaskCompletionRequest.class, request -> BROKER_RESPONSE);
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
        .perform(
            asyncRequest(
                post(baseUrl + "/1/completion")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskCompletionRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("")
        .hasVariables(Map.of("foo", "bar", "baz", 1234));
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldCompleteTaskWithActionAndVariables(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(BrokerUserTaskCompletionRequest.class, request -> BROKER_RESPONSE);
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
        .perform(
            asyncRequest(
                post(baseUrl + "/1/completion")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskCompletionRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("customAction")
        .hasVariables(Map.of("foo", "bar", "baz", 1234));
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldUpdateTaskWithAction(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(BrokerUserTaskUpdateRequest.class, request -> BROKER_RESPONSE);
    final var request =
        """
        {
          "action": "customAction"
        }""";

    // when / then
    webClient
        .perform(
            asyncRequest(
                patch(baseUrl + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskUpdateRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("customAction")
        .hasNoChangedAttributes()
        .hasDueDate("")
        .hasFollowUpDate("")
        .hasNoCandidateGroupsList()
        .hasNoCandidateUsersList();
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldUpdateTaskWithChanges(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(BrokerUserTaskUpdateRequest.class, request -> BROKER_RESPONSE);
    final var request =
        """
        {
          "changeset": {
            "candidateGroups": ["foo"],
            "candidateUsers": ["bar"],
            "dueDate": "%s",
            "followUpDate": "%s"
          }
        }"""
            .formatted(TEST_TIME, TEST_TIME);

    // when / then
    webClient
        .perform(
            asyncRequest(
                patch(baseUrl + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskUpdateRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("")
        .hasChangedAttributes(
            UserTaskRecord.CANDIDATE_USERS,
            UserTaskRecord.CANDIDATE_GROUPS,
            UserTaskRecord.DUE_DATE,
            UserTaskRecord.FOLLOW_UP_DATE)
        .hasDueDate(TEST_TIME)
        .hasFollowUpDate(TEST_TIME)
        .hasCandidateGroupsList("foo")
        .hasCandidateUsersList("bar");
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldUpdateTaskWithPartialChanges(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(BrokerUserTaskUpdateRequest.class, request -> BROKER_RESPONSE);
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
        .perform(
            asyncRequest(
                patch(baseUrl + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskUpdateRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("")
        .hasChangedAttributes(UserTaskRecord.CANDIDATE_GROUPS, UserTaskRecord.FOLLOW_UP_DATE)
        .hasDueDate("")
        .hasFollowUpDate(TEST_TIME)
        .hasCandidateGroupsList("foo")
        .hasNoCandidateUsersList();
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldUpdateTaskWithPartialEmptyValueChanges(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(BrokerUserTaskUpdateRequest.class, request -> BROKER_RESPONSE);
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
        .perform(
            asyncRequest(
                patch(baseUrl + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskUpdateRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("")
        .hasChangedAttributes(UserTaskRecord.CANDIDATE_GROUPS, UserTaskRecord.FOLLOW_UP_DATE)
        .hasDueDate("")
        .hasFollowUpDate("")
        .hasNoCandidateGroupsList()
        .hasNoCandidateUsersList();
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldUpdateTaskWithActionAndChanges(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(BrokerUserTaskUpdateRequest.class, request -> BROKER_RESPONSE);
    final var request =
        """
        {
          "action": "customAction",
          "changeset": {
            "candidateGroups": ["foo"],
            "candidateUsers": ["bar"],
            "dueDate": "%s",
            "followUpDate": "%s"
          }
        }"""
            .formatted(TEST_TIME, TEST_TIME);

    // when / then
    webClient
        .perform(
            asyncRequest(
                patch(baseUrl + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskUpdateRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("customAction")
        .hasChangedAttributes(
            UserTaskRecord.CANDIDATE_USERS,
            UserTaskRecord.CANDIDATE_GROUPS,
            UserTaskRecord.DUE_DATE,
            UserTaskRecord.FOLLOW_UP_DATE)
        .hasDueDate(TEST_TIME)
        .hasFollowUpDate(TEST_TIME)
        .hasCandidateGroupsList("foo")
        .hasCandidateUsersList("bar");
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldYieldBadRequestWhenUpdateTaskWithoutActionAndChanges(final String baseUrl)
      throws Exception {
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
        .perform(
            asyncRequest(
                patch(baseUrl + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    Mockito.verifyNoInteractions(brokerClient);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldYieldBadRequestWhenUpdateTaskWithoutMalformedDueDate(final String baseUrl)
      throws Exception {
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
        .perform(
            asyncRequest(
                patch(baseUrl + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    Mockito.verifyNoInteractions(brokerClient);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldYieldBadRequestWhenUpdateTaskWithMalformedFollowUpDate(final String baseUrl)
      throws Exception {
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
        .perform(
            asyncRequest(
                patch(baseUrl + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    Mockito.verifyNoInteractions(brokerClient);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldYieldBadRequestWhenUpdateTaskWithoutMalformedFollowUpAndDueDate(final String baseUrl)
      throws Exception {
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
        .perform(
            asyncRequest(
                patch(baseUrl + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    Mockito.verifyNoInteractions(brokerClient);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldYieldBadRequestWhenUpdateTaskWithUntrackedChanges(final String baseUrl)
      throws Exception {
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
        .perform(
            asyncRequest(
                patch(baseUrl + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    Mockito.verifyNoInteractions(brokerClient);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldYieldBadRequestWhenUpdateTaskWithOnlyUnknownProperties(final String baseUrl)
      throws Exception {
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
        .perform(
            asyncRequest(
                patch(baseUrl + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    Mockito.verifyNoInteractions(brokerClient);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldYieldNotFoundWhenTaskNotFound(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(
        BrokerUserTaskCompletionRequest.class,
        request ->
            new BrokerRejectionResponse<>(
                new BrokerRejection(
                    UserTaskIntent.COMPLETE, 1L, RejectionType.NOT_FOUND, "Task not found")));

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
        .perform(
            asyncRequest(
                post(baseUrl + "/1/completion")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskCompletionRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("")
        .hasVariables(Collections.emptyMap());
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldYieldConflictWhenInvalidState(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(
        BrokerUserTaskCompletionRequest.class,
        request ->
            new BrokerRejectionResponse<>(
                new BrokerRejection(
                    UserTaskIntent.COMPLETE,
                    1L,
                    RejectionType.INVALID_STATE,
                    "Task is not in state CREATED")));

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
        .perform(
            asyncRequest(
                post(baseUrl + "/1/completion")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskCompletionRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("")
        .hasVariables(Collections.emptyMap());
  }

  @ParameterizedTest
  @MethodSource("rejectionsAndUrls")
  public void shouldYieldBadRequestWhenRejectionOfInput(
      final Pair<RejectionType, String> parameters) throws Exception {
    // given
    brokerClient.registerHandler(
        BrokerUserTaskCompletionRequest.class,
        request ->
            new BrokerRejectionResponse<>(
                new BrokerRejection(
                    UserTaskIntent.COMPLETE, 1L, parameters.getLeft(), "Just an error")));

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
        .perform(
            asyncRequest(
                post(parameters.getRight() + "/1/completion")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskCompletionRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("")
        .hasVariables(Collections.emptyMap());
  }

  @ParameterizedTest
  @MethodSource("exceptionsAndUrls")
  public void shouldYieldInternalErrorWhenRejectionInternal(
      final Pair<RejectionType, String> parameters) throws Exception {
    // given
    brokerClient.registerHandler(
        BrokerUserTaskCompletionRequest.class,
        request ->
            new BrokerRejectionResponse<>(
                new BrokerRejection(
                    UserTaskIntent.COMPLETE, 1L, parameters.getLeft(), "Just an error")));

    final var expectedBody =
        """
         {
           "type": "about:blank",
           "status": 500,
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
        .perform(
            asyncRequest(
                post(parameters.getRight() + "/1/completion")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)))
        .andExpect(status().is5xxServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskCompletionRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("")
        .hasVariables(Collections.emptyMap());
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldAssignTaskWithoutActionAndAllowOverride(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(BrokerUserTaskAssignmentRequest.class, request -> BROKER_RESPONSE);
    final var request =
        """
        {
          "assignee": "Test Assignee"
        }""";

    // when / then
    webClient
        .perform(
            asyncRequest(
                post(baseUrl + "/2251799813685732/assignment")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskAssignmentRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("assign")
        .hasAssignee("Test Assignee");

    Assertions.assertThat(argumentCaptor.getValue().getIntent()).isEqualTo(UserTaskIntent.ASSIGN);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldAssignTaskWithActionWithoutAllowOverride(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(BrokerUserTaskAssignmentRequest.class, request -> BROKER_RESPONSE);
    final var request =
        """
        {
          "assignee": "Test Assignee",
          "action": "custom action"
        }""";

    // when / then
    webClient
        .perform(
            asyncRequest(
                post(baseUrl + "/2251799813685732/assignment")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskAssignmentRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("custom action")
        .hasAssignee("Test Assignee");

    Assertions.assertThat(argumentCaptor.getValue().getIntent()).isEqualTo(UserTaskIntent.ASSIGN);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldAssignTaskWithActionWithAllowOverrideTrue(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(BrokerUserTaskAssignmentRequest.class, request -> BROKER_RESPONSE);
    final var request =
        """
        {
          "assignee": "Test Assignee",
          "action": "custom action",
          "allowOverride": true
        }""";

    // when/then
    webClient
        .perform(
            asyncRequest(
                post(baseUrl + "/2251799813685732/assignment")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskAssignmentRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("custom action")
        .hasAssignee("Test Assignee");

    Assertions.assertThat(argumentCaptor.getValue().getIntent()).isEqualTo(UserTaskIntent.ASSIGN);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldAssignTaskWithActionWithAllowOverrideFalse(final String baseUrl) throws Exception {
    // gíven
    brokerClient.registerHandler(BrokerUserTaskAssignmentRequest.class, request -> BROKER_RESPONSE);
    final var request =
        """
        {
          "assignee": "Test Assignee",
          "action": "custom action",
          "allowOverride": false
        }""";

    // when / then
    webClient
        .perform(
            asyncRequest(
                post(baseUrl + "/2251799813685732/assignment")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskAssignmentRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("custom action")
        .hasAssignee("Test Assignee");

    Assertions.assertThat(argumentCaptor.getValue().getIntent()).isEqualTo(UserTaskIntent.CLAIM);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldAssignTaskWithoutActionWithAllowOverrideTrue(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(BrokerUserTaskAssignmentRequest.class, request -> BROKER_RESPONSE);
    final var request =
        """
        {
          "assignee": "Test Assignee",
          "allowOverride": true
        }""";

    // when / then
    webClient
        .perform(
            asyncRequest(
                post(baseUrl + "/2251799813685732/assignment")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskAssignmentRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("assign")
        .hasAssignee("Test Assignee");

    Assertions.assertThat(argumentCaptor.getValue().getIntent()).isEqualTo(UserTaskIntent.ASSIGN);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldAssignTaskWithoutActionWithAllowOverrideFalse(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(BrokerUserTaskAssignmentRequest.class, request -> BROKER_RESPONSE);
    final var request =
        """
        {
          "assignee": "Test Assignee",
          "allowOverride": false
        }""";

    // when / then
    webClient
        .perform(
            asyncRequest(
                post(baseUrl + "/2251799813685732/assignment")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskAssignmentRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("assign")
        .hasAssignee("Test Assignee");

    Assertions.assertThat(argumentCaptor.getValue().getIntent()).isEqualTo(UserTaskIntent.CLAIM);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldYieldBadRequestWhenNoAssigneeForTaskAssignment(final String baseUrl) throws Exception {
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
        .perform(
            asyncRequest(
                post(baseUrl + "/1/assignment")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    Mockito.verifyNoInteractions(brokerClient);
  }

  @ParameterizedTest
  @MethodSource("urls")
  void shouldUnassignTask(final String baseUrl) throws Exception {
    // given
    brokerClient.registerHandler(BrokerUserTaskAssignmentRequest.class, request -> BROKER_RESPONSE);
    // when / then
    webClient
        .perform(asyncRequest(delete(baseUrl + "/2251799813685732/assignee")))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskAssignmentRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("unassign")
        .hasAssignee("");

    Assertions.assertThat(argumentCaptor.getValue().getIntent()).isEqualTo(UserTaskIntent.ASSIGN);
  }

  @TestConfiguration
  static class TestUserTaskApplication {
    @Bean
    public StubbedBrokerClient brokerClient() {
      return new StubbedBrokerClient();
    }
  }
}
