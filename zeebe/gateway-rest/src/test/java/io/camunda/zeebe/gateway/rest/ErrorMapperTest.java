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
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerErrorResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.gateway.rest.ErrorMapperTest.TestErrorMapperApplication;
import io.camunda.zeebe.gateway.rest.ErrorMapperTest.TestErrorMapperConfiguration;
import io.camunda.zeebe.gateway.rest.controller.ResponseObserverProvider;
import io.camunda.zeebe.gateway.rest.controller.UserTaskController;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(
    classes = {
      TestErrorMapperApplication.class,
      TestErrorMapperConfiguration.class,
      UserTaskController.class
    },
    webEnvironment = WebEnvironment.RANDOM_PORT)
public class ErrorMapperTest {

  private static final String USER_TASKS_BASE_URL = "/v1/user-tasks";

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
  public void shouldThrowExceptionWithRequestBodyMissing() throws Exception {
    // given
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Required request body is missing");
    expectedBody.setTitle("Bad Request");
    expectedBody.setInstance(URI.create(USER_TASKS_BASE_URL + "/1/assignment"));

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/assignment")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);

    Mockito.verifyNoInteractions(brokerClient);
  }

  @SpringBootApplication
  static class TestErrorMapperApplication {
    // required to provide the web server context
  }

  @TestConfiguration
  static class TestErrorMapperConfiguration {

    @Bean
    public ActivateJobsHandler<JobActivationResponse> handler() {
      return Mockito.mock(ActivateJobsHandler.class);
    }

    @Bean
    public ResponseObserverProvider responseObserverProvider() {
      return Mockito.mock(ResponseObserverProvider.class);
    }
  }
}
