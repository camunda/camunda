/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.rest;

import static org.mockito.ArgumentMatchers.any;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejectionResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.gateway.rest.TopologyControllerTest.TestTopologyApplication;
import io.camunda.zeebe.gateway.rest.impl.broker.request.BrokerUserTaskCompletionRequest;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.net.URI;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(
    classes = {TestTopologyApplication.class, TopologyController.class},
    webEnvironment = WebEnvironment.RANDOM_PORT)
public class UserTaskControllerTest {

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
        .uri("api/v1/user-tasks/2251799813685732/completion")
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
        .uri("api/v1/user-tasks/1/completion")
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
        .uri("api/v1/user-tasks/1/completion")
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
        .uri("api/v1/user-tasks/1/completion")
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
    expectedBody.setInstance(URI.create("/api/v1/user-tasks/1/completion"));

    // when / then
    webClient
        .post()
        .uri("api/v1/user-tasks/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isNotFound()
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
    expectedBody.setInstance(URI.create("/api/v1/user-tasks/1/completion"));

    // when / then
    webClient
        .post()
        .uri("api/v1/user-tasks/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT)
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
    expectedBody.setInstance(URI.create("/api/v1/user-tasks/1/completion"));

    // when / then
    webClient
        .post()
        .uri("api/v1/user-tasks/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isBadRequest()
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
    expectedBody.setInstance(URI.create("/api/v1/user-tasks/1/completion"));

    // when / then
    webClient
        .post()
        .uri("api/v1/user-tasks/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);

    final var argumentCaptor = ArgumentCaptor.forClass(BrokerUserTaskCompletionRequest.class);
    Mockito.verify(brokerClient).sendRequest(argumentCaptor.capture());
    Assertions.assertThat(argumentCaptor.getValue().getRequestWriter())
        .hasUserTaskKey(1L)
        .hasAction("")
        .hasVariables(Collections.emptyMap());
  }
}
