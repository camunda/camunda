/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.rest;

import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.core.JsonParseException;
import io.atomix.cluster.messaging.MessagingException.ConnectionClosed;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.PartitionNotFoundException;
import io.camunda.zeebe.broker.client.api.RequestRetriesExhaustedException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerErrorResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.gateway.rest.TopologyControllerTest.TestTopologyApplication;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.msgpack.spec.MsgpackException;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.netty.channel.ConnectTimeoutException;
import java.net.ConnectException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.View;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(
    classes = {TestTopologyApplication.class, TopologyController.class},
    webEnvironment = WebEnvironment.RANDOM_PORT)
public class ErrorMapperTest {

  private static final String USER_TASKS_BASE_URL = "/v1/user-tasks";

  @MockBean BrokerClient brokerClient;
  Supplier<CompletableFuture<BrokerResponse<Object>>> brokerResponseFutureSupplier;
  @Autowired private View error;

  @Autowired private WebTestClient webClient;

  @BeforeEach
  void setUp() {
    brokerResponseFutureSupplier =
        () -> CompletableFuture.supplyAsync(() -> new BrokerResponse<>(new UserTaskRecord()));
    Mockito.when(brokerClient.sendRequest(any()))
        .thenAnswer(i -> brokerResponseFutureSupplier.get());
  }

  @Test
  public void shouldYieldNotFoundWhenBrokerErrorNotFound() {
    // given
    brokerResponseFutureSupplier =
        () ->
            CompletableFuture.supplyAsync(
                () ->
                    new BrokerErrorResponse<>(
                        new BrokerError(ErrorCode.PROCESS_NOT_FOUND, "Just an error")));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Just an error");
    expectedBody.setTitle(ErrorCode.PROCESS_NOT_FOUND.name());
    expectedBody.setInstance(URI.create(USER_TASKS_BASE_URL + "/1/completion"));

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
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  public void shouldYieldTooManyRequestsWhenBrokerErrorExhausted() {
    // given
    brokerResponseFutureSupplier =
        () ->
            CompletableFuture.supplyAsync(
                () ->
                    new BrokerErrorResponse<>(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "Just an error")));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Just an error");
    expectedBody.setTitle(ErrorCode.RESOURCE_EXHAUSTED.name());
    expectedBody.setInstance(URI.create(USER_TASKS_BASE_URL + "/1/completion"));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  public void shouldYieldUnavailableWhenBrokerErrorLeaderMismatch() {
    // given
    brokerResponseFutureSupplier =
        () ->
            CompletableFuture.supplyAsync(
                () ->
                    new BrokerErrorResponse<>(
                        new BrokerError(ErrorCode.PARTITION_LEADER_MISMATCH, "Just an error")));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Just an error");
    expectedBody.setTitle(ErrorCode.PARTITION_LEADER_MISMATCH.name());
    expectedBody.setInstance(URI.create(USER_TASKS_BASE_URL + "/1/completion"));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @ParameterizedTest
  @EnumSource(
      value = ErrorCode.class,
      names = {
        "INTERNAL_ERROR",
        "UNSUPPORTED_MESSAGE",
        "INVALID_CLIENT_VERSION",
        "MALFORMED_REQUEST",
        "INVALID_MESSAGE_TEMPLATE",
        "INVALID_DEPLOYMENT_PARTITION",
        "SBE_UNKNOWN",
        "NULL_VAL"
      })
  public void shouldYieldInternalErrorWhenBrokerError(final ErrorCode errorCode) {
    // given
    brokerResponseFutureSupplier =
        () ->
            CompletableFuture.supplyAsync(
                () -> new BrokerErrorResponse<>(new BrokerError(errorCode, "Just an error")));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Received an unexpected error from the broker, code: "
                + errorCode
                + ", message: Just an error");
    expectedBody.setTitle(errorCode.name());
    expectedBody.setInstance(URI.create(USER_TASKS_BASE_URL + "/1/completion"));

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
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  public void shouldYieldInternalErrorWhenException() {
    // given
    brokerResponseFutureSupplier =
        () ->
            CompletableFuture.supplyAsync(
                () -> {
                  throw new NullPointerException("Just an error");
                });

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Unexpected error occurred during the request processing: Just an error");
    expectedBody.setTitle(NullPointerException.class.getName());
    expectedBody.setInstance(URI.create(USER_TASKS_BASE_URL + "/1/completion"));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  public void shouldReturnGatewayTimeoutOnTimeoutException() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new TimeoutException("Oh noes, timeouts!")));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.GATEWAY_TIMEOUT,
            "Expected to handle REST API request, but request timed out between gateway and broker");
    expectedBody.setTitle(TimeoutException.class.getName());
    expectedBody.setInstance(URI.create(USER_TASKS_BASE_URL + "/1/completion"));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  public void shouldReturnGatewayTimeoutOnConnectionClosed() {
    // given
    final var errorMsg = "Oh noes, connection closed!";
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new ConnectionClosed(errorMsg)));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.GATEWAY_TIMEOUT,
            "Expected to handle REST API request, but the connection was cut prematurely with the broker; "
                + "the request may or may not have been accepted, and may not be safe to retry");
    expectedBody.setTitle(ConnectionClosed.class.getName());
    expectedBody.setInstance(URI.create(USER_TASKS_BASE_URL + "/1/completion"));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.GATEWAY_TIMEOUT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  public void shouldReturnServiceUnavailableOnConnectTimeoutException() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new ConnectTimeoutException("Oh noes, connection timeouts!")));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Expected to handle REST API request, but a connection timeout exception occurred");
    expectedBody.setTitle(ConnectTimeoutException.class.getName());
    expectedBody.setInstance(URI.create(USER_TASKS_BASE_URL + "/1/completion"));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  public void shouldReturnServiceUnavailableOnConnectException() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(new ConnectException("Oh noes, connection timeouts!")));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Expected to handle REST API request, but there was a connection error with one of the brokers");
    expectedBody.setTitle(ConnectException.class.getName());
    expectedBody.setInstance(URI.create(USER_TASKS_BASE_URL + "/1/completion"));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  public void shouldReturnServiceUnavailableOnPartitionNotFoundException() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new PartitionNotFoundException(1)));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Expected to handle REST API request, but request could not be delivered");
    expectedBody.setTitle(PartitionNotFoundException.class.getName());
    expectedBody.setInstance(URI.create(USER_TASKS_BASE_URL + "/1/completion"));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  public void shouldReturnBadRequestOnMsgpackException() {
    // given;
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new MsgpackException("Oh noes, msg parsing!")));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Expected to handle REST API request, but messagepack property was invalid");
    expectedBody.setTitle(MsgpackException.class.getName());
    expectedBody.setInstance(URI.create(USER_TASKS_BASE_URL + "/1/completion"));

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
  }

  @Test
  public void shouldReturnBadRequestOnJsonParseException() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(new JsonParseException("Oh noes, json parsing!")));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Expected to handle REST API request, but JSON property was invalid");
    expectedBody.setTitle(JsonParseException.class.getName());
    expectedBody.setInstance(URI.create(USER_TASKS_BASE_URL + "/1/completion"));

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
  }

  @Test
  public void shouldReturnBadRequestOnIllegalArgumentException() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new IllegalArgumentException("Oh noes, illegal arguments!")));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Expected to handle REST API request, but JSON property was invalid");
    expectedBody.setTitle(IllegalArgumentException.class.getName());
    expectedBody.setInstance(URI.create(USER_TASKS_BASE_URL + "/1/completion"));

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
  }

  @Test
  public void shouldReturnTooManyRequestsOnRequestRetriesExhaustedException() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new RequestRetriesExhaustedException()));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            "Expected to handle REST API request, but all retries have been exhausted");
    expectedBody.setTitle(RequestRetriesExhaustedException.class.getName());
    expectedBody.setInstance(URI.create(USER_TASKS_BASE_URL + "/1/completion"));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .body(Mono.just(request), UserTaskCompletionRequest.class)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }
}
