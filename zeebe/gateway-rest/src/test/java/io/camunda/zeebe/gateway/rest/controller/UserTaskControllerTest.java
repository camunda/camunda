/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejectionResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(UserTaskController.class)
public class UserTaskControllerTest extends RestControllerTest {

  private static final String USER_TASKS_BASE_URL = "/v1/user-tasks";
  private static final String TEST_TIME =
      ZonedDateTime.of(2023, 11, 11, 11, 11, 11, 11, ZoneId.of("UTC")).toString();

  @MockBean BrokerClient brokerClient;
  Supplier<CompletableFuture<BrokerResponse<Object>>> brokerResponseFutureSupplier;

  @BeforeEach
  void setUp() {
    brokerResponseFutureSupplier =
        () -> CompletableFuture.supplyAsync(() -> new BrokerResponse<>(new UserTaskRecord()));
    Mockito.when(brokerClient.sendRequest(any()))
        .thenAnswer(i -> brokerResponseFutureSupplier.get());
  }

  @Test
  public void shouldCompleteTaskWithoutActionAndVariables() throws Exception {
    // when / then
    webClient
        .perform(
            asyncRequest(
                post(USER_TASKS_BASE_URL + "/2251799813685732/completion")
                    .accept(MediaType.APPLICATION_JSON)))
        .andExpect(status().isNoContent())
        .andExpect(content().bytes(new byte[0]));

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskCompletionRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("")
        .hasVariables(Collections.emptyMap());
  }

  @Test
  public void shouldCompleteTaskWithAction() throws Exception {
    // given
    final var request =
        """
        {
          "action": "customAction"
        }""";

    // when / then
    webClient
        .perform(
            asyncRequest(
                post(USER_TASKS_BASE_URL + "/1/completion")
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

  @Test
  public void shouldCompleteTaskWithVariables() throws Exception {
    // given
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
                post(USER_TASKS_BASE_URL + "/1/completion")
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

  @Test
  public void shouldCompleteTaskWithActionAndVariables() throws Exception {
    // given
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
                post(USER_TASKS_BASE_URL + "/1/completion")
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

  @Test
  public void shouldUpdateTaskWithAction() throws Exception {
    // given
    final var request =
        """
        {
          "action": "customAction"
        }""";

    // when / then
    webClient
        .perform(
            asyncRequest(
                patch(USER_TASKS_BASE_URL + "/1")
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

  @Test
  public void shouldUpdateTaskWithChanges() throws Exception {
    // given
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
                patch(USER_TASKS_BASE_URL + "/1")
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

  @Test
  public void shouldUpdateTaskWithPartialChanges() throws Exception {
    // given
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
                patch(USER_TASKS_BASE_URL + "/1")
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

  @Test
  public void shouldUpdateTaskWithPartialEmptyValueChanges() throws Exception {
    // given
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
                patch(USER_TASKS_BASE_URL + "/1")
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

  //
  @Test
  public void shouldUpdateTaskWithActionAndChanges() throws Exception {
    // given
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
                patch(USER_TASKS_BASE_URL + "/1")
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

  @Test
  public void shouldYieldBadRequestWhenUpdateTaskWithoutActionAndChanges() throws Exception {
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
            .formatted(USER_TASKS_BASE_URL + "/1");

    // when / then
    webClient
        .perform(
            asyncRequest(
                patch(USER_TASKS_BASE_URL + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    Mockito.verifyNoInteractions(brokerClient);
  }

  @Test
  public void shouldYieldBadRequestWhenUpdateTaskWithoutMalformedDueDate() throws Exception {
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
            .formatted(USER_TASKS_BASE_URL + "/1");

    // when / then
    webClient
        .perform(
            asyncRequest(
                patch(USER_TASKS_BASE_URL + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    Mockito.verifyNoInteractions(brokerClient);
  }

  @Test
  public void shouldYieldBadRequestWhenUpdateTaskWithMalformedFollowUpDate() throws Exception {
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
            .formatted(USER_TASKS_BASE_URL + "/1");

    // when / then
    webClient
        .perform(
            asyncRequest(
                patch(USER_TASKS_BASE_URL + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    Mockito.verifyNoInteractions(brokerClient);
  }

  @Test
  public void shouldYieldBadRequestWhenUpdateTaskWithoutMalformedFollowUpAndDueDate()
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
            .formatted(USER_TASKS_BASE_URL + "/1");

    // when / then
    webClient
        .perform(
            asyncRequest(
                patch(USER_TASKS_BASE_URL + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    Mockito.verifyNoInteractions(brokerClient);
  }

  @Test
  public void shouldYieldBadRequestWhenUpdateTaskWithUntrackedChanges() throws Exception {
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
            .formatted(USER_TASKS_BASE_URL + "/1");

    // when / then
    webClient
        .perform(
            asyncRequest(
                patch(USER_TASKS_BASE_URL + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    Mockito.verifyNoInteractions(brokerClient);
  }

  @Test
  public void shouldYieldBadRequestWhenUpdateTaskWithOnlyUnknownProperties() throws Exception {
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
            .formatted(USER_TASKS_BASE_URL + "/1");

    // when / then
    webClient
        .perform(
            asyncRequest(
                patch(USER_TASKS_BASE_URL + "/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    Mockito.verifyNoInteractions(brokerClient);
  }

  @Test
  public void shouldYieldNotFoundWhenTaskNotFound() throws Exception {
    // given
    brokerResponseFutureSupplier =
        () ->
            CompletableFuture.supplyAsync(
                () ->
                    new BrokerRejectionResponse<>(
                        new BrokerRejection(
                            UserTaskIntent.COMPLETE,
                            1L,
                            RejectionType.NOT_FOUND,
                            "Task not found")));

    final var expectedBody =
        """
        {
          "type": "about:blank",
          "status": 404,
          "title": "NOT_FOUND",
          "detail": "Command 'COMPLETE' rejected with code 'NOT_FOUND': Task not found",
          "instance": "%s"
        }"""
            .formatted(USER_TASKS_BASE_URL + "/1/completion");

    // when / then
    webClient
        .perform(
            asyncRequest(
                post(USER_TASKS_BASE_URL + "/1/completion")
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

  @Test
  public void shouldYieldConflictWhenInvalidState() throws Exception {
    // given
    brokerResponseFutureSupplier =
        () ->
            CompletableFuture.supplyAsync(
                () ->
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
            .formatted(USER_TASKS_BASE_URL + "/1/completion");

    // when / then
    webClient
        .perform(
            asyncRequest(
                post(USER_TASKS_BASE_URL + "/1/completion")
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
  @EnumSource(
      value = RejectionType.class,
      names = {"INVALID_ARGUMENT", "ALREADY_EXISTS"})
  public void shouldYieldBadRequestWhenRejectionOfInput(final RejectionType rejectionType)
      throws Exception {
    // given
    brokerResponseFutureSupplier =
        () ->
            CompletableFuture.supplyAsync(
                () ->
                    new BrokerRejectionResponse<>(
                        new BrokerRejection(
                            UserTaskIntent.COMPLETE, 1L, rejectionType, "Just an error")));

    final var expectedBody =
        """
        {
          "type": "about:blank",
          "status": 400,
          "title": "%s",
          "detail": "Command 'COMPLETE' rejected with code '%s': Just an error",
          "instance": "%s"
        }"""
            .formatted(rejectionType.name(), rejectionType, USER_TASKS_BASE_URL + "/1/completion");

    // when / then
    webClient
        .perform(
            asyncRequest(
                post(USER_TASKS_BASE_URL + "/1/completion")
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
  @EnumSource(
      value = RejectionType.class,
      names = {"PROCESSING_ERROR", "EXCEEDED_BATCH_RECORD_SIZE", "SBE_UNKNOWN", "NULL_VAL"})
  public void shouldYieldInternalErrorWhenRejectionInternal(final RejectionType rejectionType)
      throws Exception {
    // given
    brokerResponseFutureSupplier =
        () ->
            CompletableFuture.supplyAsync(
                () ->
                    new BrokerRejectionResponse<>(
                        new BrokerRejection(
                            UserTaskIntent.COMPLETE, 1L, rejectionType, "Just an error")));

    final var expectedBody =
        """
         {
           "type": "about:blank",
           "status": 500,
           "title": "%s",
           "detail": "Command 'COMPLETE' rejected with code '%s': Just an error",
           "instance": "%s"
         }"""
            .formatted(rejectionType.name(), rejectionType, USER_TASKS_BASE_URL + "/1/completion");

    // when / then
    webClient
        .perform(
            asyncRequest(
                post(USER_TASKS_BASE_URL + "/1/completion")
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

  @Test
  public void shouldAssignTaskWithoutActionAndAllowOverride() throws Exception {
    // given
    final var request =
        """
        {
          "assignee": "Test Assignee"
        }""";

    // when / then
    webClient
        .perform(
            asyncRequest(
                post(USER_TASKS_BASE_URL + "/2251799813685732/assignment")
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

  @Test
  public void shouldAssignTaskWithActionWithoutAllowOverride() throws Exception {
    // given
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
                post(USER_TASKS_BASE_URL + "/2251799813685732/assignment")
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

  @Test
  public void shouldAssignTaskWithActionWithAllowOverrideTrue() throws Exception {
    // given
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
                post(USER_TASKS_BASE_URL + "/2251799813685732/assignment")
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

  @Test
  public void shouldAssignTaskWithActionWithAllowOverrideFalse() throws Exception {
    // gíven
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
                post(USER_TASKS_BASE_URL + "/2251799813685732/assignment")
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

  @Test
  public void shouldAssignTaskWithoutActionWithAllowOverrideTrue() throws Exception {
    // given
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
                post(USER_TASKS_BASE_URL + "/2251799813685732/assignment")
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

  @Test
  public void shouldAssignTaskWithoutActionWithAllowOverrideFalse() throws Exception {
    // given
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
                post(USER_TASKS_BASE_URL + "/2251799813685732/assignment")
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

  @Test
  public void shouldYieldBadRequestWhenNoAssigneeForTaskAssignment() throws Exception {
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
            .formatted(USER_TASKS_BASE_URL + "/1/assignment");

    // when / then
    webClient
        .perform(
            asyncRequest(
                post(USER_TASKS_BASE_URL + "/1/assignment")
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(request)))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().json(expectedBody));

    Mockito.verifyNoInteractions(brokerClient);
  }

  @Test
  public void shouldUnassignTask() throws Exception {
    // when / then
    webClient
        .perform(asyncRequest(delete(USER_TASKS_BASE_URL + "/2251799813685732/assignee")))
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
}
