/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static org.mockito.ArgumentMatchers.any;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejectionResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserTaskCompletionRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserTaskUpdateRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskUpdateRequestChangeset;
import io.camunda.zeebe.gateway.rest.TopologyControllerTest.TestTopologyApplication;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.net.URI;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

@SpringBootTest(
    classes = {TestTopologyApplication.class, TopologyController.class},
    webEnvironment = WebEnvironment.RANDOM_PORT)
public class UserTaskControllerTest {

  private static final String USER_TASKS_BASE_URL = "v1/user-tasks";

  private static final String TEST_TIME =
      ZonedDateTime.of(2023, 11, 11, 11, 11, 11, 11, ZoneId.of("UTC")).toString();

  @MockBean BrokerClient brokerClient;
  Supplier<CompletableFuture<BrokerResponse<Object>>> brokerResponseFutureSupplier;

  @Autowired private WebTestClient webClient;

  @BeforeEach
  void setUp() {
    brokerResponseFutureSupplier =
        () -> CompletableFuture.supplyAsync(() -> new BrokerResponse<>(new UserTaskRecord()));
    Mockito.when(brokerClient.sendRequest(any()))
        .thenAnswer(i -> brokerResponseFutureSupplier.get());
  }

  @Test
  public void shouldCompleteTaskWithoutActionAndVariables() {
    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/2251799813685732/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskCompletionRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("")
        .hasVariables(Collections.emptyMap());
  }

  @Test
  public void shouldCompleteTaskWithAction() {
    // given
    final var request = new UserTaskCompletionRequest().action("customAction");

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskCompletionRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("customAction")
        .hasVariables(Collections.emptyMap());
  }

  @Test
  public void shouldCompleteTaskWithVariables() {
    // given
    final var request =
        new UserTaskCompletionRequest().variables(Map.of("foo", "bar", "baz", 1234));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskCompletionRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("")
        .hasVariables(Map.of("foo", "bar", "baz", 1234));
  }

  @Test
  public void shouldCompleteTaskWithActionAndVariables() {
    // given
    final var request =
        new UserTaskCompletionRequest()
            .action("customAction")
            .variables(Map.of("foo", "bar", "baz", 1234));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskCompletionRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("customAction")
        .hasVariables(Map.of("foo", "bar", "baz", 1234));
  }

  @Test
  public void shouldUpdateTaskWithAction() {
    // given
    final var request = new UserTaskUpdateRequest().action("customAction");
    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

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
  public void shouldUpdateTaskWithChanges() {
    // given
    final var request =
        new UserTaskUpdateRequest()
            .changeset(
                new UserTaskUpdateRequestChangeset()
                    .addCandidateGroupsItem("foo")
                    .addCandidateUsersItem("bar")
                    .dueDate(TEST_TIME)
                    .followUpDate(TEST_TIME));

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

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
  public void shouldUpdateTaskWithPartialChanges() {
    // given
    final var request =
        new UserTaskUpdateRequest()
            .changeset(
                new UserTaskUpdateRequestChangeset()
                    .addCandidateGroupsItem("foo")
                    .followUpDate(TEST_TIME));

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

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
  public void shouldUpdateTaskWithPartialChangesRawJson() {
    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            BodyInserters.fromValue(
                "{\"changeset\": { \"followUpDate\": \""
                    + TEST_TIME
                    + "\", \"candidateGroups\": [\"foo\"]}}"))
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

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
  public void shouldUpdateTaskWithPartialEmptyValueChanges() {
    // given
    final var request =
        new UserTaskUpdateRequest()
            .changeset(
                new UserTaskUpdateRequestChangeset().candidateGroups(List.of()).followUpDate(""));

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

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

  @Test
  public void shouldUpdateTaskWithPartialEmptyValueChangesRawJson() {
    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            BodyInserters.fromValue(
                "{\"changeset\": { \"followUpDate\": \"\", \"candidateGroups\": []}}"))
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

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

  @Test
  public void shouldUpdateTaskWithActionAndChanges() {
    // given
    final var request =
        new UserTaskUpdateRequest()
            .action("customAction")
            .changeset(
                new UserTaskUpdateRequestChangeset()
                    .addCandidateGroupsItem("foo")
                    .addCandidateUsersItem("bar")
                    .dueDate(TEST_TIME)
                    .followUpDate(TEST_TIME));

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

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
  public void shouldYieldBadRequestWhenUpdateTaskWithoutActionAndChanges() {
    // given
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "No update data provided. Provide at least an \"action\" or a non-null value "
                + "for a supported attribute in the \"changeset\".");
    expectedBody.setTitle("INVALID_ARGUMENT");
    expectedBody.setInstance(URI.create("/" + USER_TASKS_BASE_URL + "/1"));

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);

    Mockito.verifyNoInteractions(brokerClient);
  }

  @Test
  public void shouldYieldBadRequestWhenUpdateTaskWithoutMalformedDueDate() {
    // given
    final var request =
        new UserTaskUpdateRequest().changeset(new UserTaskUpdateRequestChangeset().dueDate("foo"));

    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "The provided due date 'foo' cannot be parsed as a date according to RFC 3339, section 5.6.");
    expectedBody.setTitle("INVALID_ARGUMENT");
    expectedBody.setInstance(URI.create("/" + USER_TASKS_BASE_URL + "/1"));

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);

    Mockito.verifyNoInteractions(brokerClient);
  }

  @Test
  public void shouldYieldBadRequestWhenUpdateTaskWithMalformedFollowUpDate() {
    // given
    final var request =
        new UserTaskUpdateRequest()
            .changeset(new UserTaskUpdateRequestChangeset().followUpDate("foo"));

    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "The provided follow-up date 'foo' cannot be parsed as a date according to RFC 3339, section 5.6.");
    expectedBody.setTitle("INVALID_ARGUMENT");
    expectedBody.setInstance(URI.create("/" + USER_TASKS_BASE_URL + "/1"));

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);

    Mockito.verifyNoInteractions(brokerClient);
  }

  @Test
  public void shouldYieldBadRequestWhenUpdateTaskWithoutMalformedFollowUpAndDueDate() {
    // given
    final var request =
        new UserTaskUpdateRequest()
            .changeset(new UserTaskUpdateRequestChangeset().dueDate("bar").followUpDate("foo"));

    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "The provided due date 'bar' cannot be parsed as a date according to RFC 3339, section 5.6. "
                + "The provided follow-up date 'foo' cannot be parsed as a date according to RFC 3339, section 5.6.");
    expectedBody.setTitle("INVALID_ARGUMENT");
    expectedBody.setInstance(URI.create("/" + USER_TASKS_BASE_URL + "/1"));

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);

    Mockito.verifyNoInteractions(brokerClient);
  }

  @Test
  public void shouldYieldBadRequestWhenUpdateTaskWithUntrackedChanges() {
    // given
    final var request = new UserTaskUpdateRequest().changeset(new UserTaskUpdateRequestChangeset());

    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "No update data provided. Provide at least an \"action\" or a non-null value "
                + "for a supported attribute in the \"changeset\".");
    expectedBody.setTitle("INVALID_ARGUMENT");
    expectedBody.setInstance(URI.create("/" + USER_TASKS_BASE_URL + "/1"));

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskUpdateRequest.class)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);

    Mockito.verifyNoInteractions(brokerClient);
  }

  @Test
  public void shouldYieldBadRequestWhenUpdateTaskWithUntrackedChangesRawJson() {
    // given
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "No update data provided. Provide at least an \"action\" or a non-null value "
                + "for a supported attribute in the \"changeset\".");
    expectedBody.setTitle("INVALID_ARGUMENT");
    expectedBody.setInstance(URI.create("/" + USER_TASKS_BASE_URL + "/1"));

    // when / then
    webClient
        .patch()
        .uri(USER_TASKS_BASE_URL + "/1")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(BodyInserters.fromValue("{ \"changeset\": {\"elementInstanceKey\": 123456}}"))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);

    Mockito.verifyNoInteractions(brokerClient);
  }

  @Test
  public void shouldYieldNotFoundWhenTaskNotFound() {
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

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            "Command 'COMPLETE' rejected with code 'NOT_FOUND': Task not found");
    expectedBody.setTitle("NOT_FOUND");
    expectedBody.setInstance(URI.create("/" + USER_TASKS_BASE_URL + "/1/completion"));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskCompletionRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("")
        .hasVariables(Collections.emptyMap());
  }

  @Test
  public void shouldYieldConflictWhenInvalidState() {
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

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "Command 'COMPLETE' rejected with code 'INVALID_STATE': Task is not in state CREATED");
    expectedBody.setTitle("INVALID_STATE");
    expectedBody.setInstance(URI.create("/" + USER_TASKS_BASE_URL + "/1/completion"));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);

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
  public void shouldYieldBadRequestWhenRejectionOfInput(final RejectionType rejectionType) {
    // given
    brokerResponseFutureSupplier =
        () ->
            CompletableFuture.supplyAsync(
                () ->
                    new BrokerRejectionResponse<>(
                        new BrokerRejection(
                            UserTaskIntent.COMPLETE, 1L, rejectionType, "Just an error")));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Command 'COMPLETE' rejected with code '" + rejectionType + "': Just an error");
    expectedBody.setTitle(rejectionType.name());
    expectedBody.setInstance(URI.create("/" + USER_TASKS_BASE_URL + "/1/completion"));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);

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
  public void shouldYieldInternalErrorWhenRejectionInternal(final RejectionType rejectionType) {
    // given
    brokerResponseFutureSupplier =
        () ->
            CompletableFuture.supplyAsync(
                () ->
                    new BrokerRejectionResponse<>(
                        new BrokerRejection(
                            UserTaskIntent.COMPLETE, 1L, rejectionType, "Just an error")));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Command 'COMPLETE' rejected with code '" + rejectionType + "': Just an error");
    expectedBody.setTitle(rejectionType.name());
    expectedBody.setInstance(URI.create("/" + USER_TASKS_BASE_URL + "/1/completion"));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskCompletionRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("")
        .hasVariables(Collections.emptyMap());
  }

  @Test
  public void shouldAssignTaskWithoutActionAndAllowOverride() {
    // when / then
    final var request = new UserTaskAssignmentRequest().assignee("Test Assignee");

    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskAssignmentRequest.class)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskAssignmentRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("assign")
        .hasAssignee("Test Assignee");

    Assertions.assertThat(argumentCaptor.getValue().getIntent()).isEqualTo(UserTaskIntent.ASSIGN);
  }

  @Test
  public void shouldAssignTaskWithActionWithoutAllowOverride() {
    // when / then
    final var request =
        new UserTaskAssignmentRequest().assignee("Test Assignee").action("custom action");

    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskAssignmentRequest.class)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskAssignmentRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("custom action")
        .hasAssignee("Test Assignee");

    Assertions.assertThat(argumentCaptor.getValue().getIntent()).isEqualTo(UserTaskIntent.ASSIGN);
  }

  @Test
  public void shouldAssignTaskWithActionWithAllowOverrideTrue() {
    // when / then
    final var request =
        new UserTaskAssignmentRequest()
            .assignee("Test Assignee")
            .action("custom action")
            .allowOverride(true);

    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskAssignmentRequest.class)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskAssignmentRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("custom action")
        .hasAssignee("Test Assignee");

    Assertions.assertThat(argumentCaptor.getValue().getIntent()).isEqualTo(UserTaskIntent.ASSIGN);
  }

  @Test
  public void shouldAssignTaskWithActionWithAllowOverrideFalse() {
    // when / then
    final var request =
        new UserTaskAssignmentRequest()
            .assignee("Test Assignee")
            .action("custom action")
            .allowOverride(false);

    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskAssignmentRequest.class)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskAssignmentRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("custom action")
        .hasAssignee("Test Assignee");

    Assertions.assertThat(argumentCaptor.getValue().getIntent()).isEqualTo(UserTaskIntent.CLAIM);
  }

  @Test
  public void shouldAssignTaskWithoutActionWithAllowOverrideTrue() {
    // when / then
    final var request =
        new UserTaskAssignmentRequest().assignee("Test Assignee").allowOverride(true);

    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskAssignmentRequest.class)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskAssignmentRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("assign")
        .hasAssignee("Test Assignee");

    Assertions.assertThat(argumentCaptor.getValue().getIntent()).isEqualTo(UserTaskIntent.ASSIGN);
  }

  @Test
  public void shouldAssignTaskWithoutActionWithAllowOverrideFalse() {
    // when / then
    final var request =
        new UserTaskAssignmentRequest().assignee("Test Assignee").allowOverride(false);

    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/2251799813685732/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskAssignmentRequest.class)
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskAssignmentRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("assign")
        .hasAssignee("Test Assignee");

    Assertions.assertThat(argumentCaptor.getValue().getIntent()).isEqualTo(UserTaskIntent.CLAIM);
  }

  @Test
  public void shouldYieldBadRequestWhenNoAssigneeForTaskAssignment() {
    // given
    final var request = new UserTaskAssignmentRequest();

    final var expectedBody =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "No assignee provided");
    expectedBody.setTitle("INVALID_ARGUMENT");
    expectedBody.setInstance(URI.create("/" + USER_TASKS_BASE_URL + "/1/assignment"));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskAssignmentRequest.class)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);

    Mockito.verifyNoInteractions(brokerClient);
  }

  @Test
  public void shouldUnassignTask() {
    // when / then
    webClient
        .delete()
        .uri(USER_TASKS_BASE_URL + "/2251799813685732/assignee")
        .exchange()
        .expectStatus()
        .isNoContent()
        .expectBody()
        .isEmpty();

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskAssignmentRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(2251799813685732L)
        .hasAction("unassign")
        .hasAssignee("");

    Assertions.assertThat(argumentCaptor.getValue().getIntent()).isEqualTo(UserTaskIntent.ASSIGN);
  }

  @Test
  public void shouldReturnProblemDetailWithRequestBodyMissing() {
    // given
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Required request body is missing");
    expectedBody.setTitle("Bad Request");
    expectedBody.setInstance(URI.create("/" + USER_TASKS_BASE_URL + "/1/assignment"));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);

    Mockito.verifyNoInteractions(brokerClient);
  }
}
