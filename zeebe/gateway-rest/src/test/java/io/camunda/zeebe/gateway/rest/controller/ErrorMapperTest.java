/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.service.exception.ServiceException.Status.ABORTED;
import static io.camunda.service.exception.ServiceException.Status.DEADLINE_EXCEEDED;
import static io.camunda.service.exception.ServiceException.Status.INTERNAL;
import static io.camunda.service.exception.ServiceException.Status.INVALID_ARGUMENT;
import static io.camunda.service.exception.ServiceException.Status.NOT_FOUND;
import static io.camunda.service.exception.ServiceException.Status.RESOURCE_EXHAUSTED;
import static io.camunda.service.exception.ServiceException.Status.UNAVAILABLE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParseException;
import io.atomix.cluster.messaging.MessagingException.ConnectionClosed;
import io.camunda.gateway.protocol.model.UserTaskCompletionRequest;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.UserTaskServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.PartitionNotFoundException;
import io.camunda.zeebe.broker.client.api.RequestRetriesExhaustedException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.msgpack.spec.MsgpackException;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.netty.channel.ConnectTimeoutException;
import java.net.ConnectException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.servlet.View;
import reactor.core.publisher.Mono;

@WebMvcTest(UserTaskController.class)
public class ErrorMapperTest extends RestControllerTest {

  private static final String USER_TASKS_BASE_URL = "/v1/user-tasks";

  @MockitoBean UserTaskServices userTaskServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @Autowired private View error;

  @BeforeEach
  void setUp() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    Mockito.when(userTaskServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(userTaskServices);
  }

  @Test
  void shouldYieldNotFoundWhenBrokerErrorNotFound() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerError(
                    new BrokerError(ErrorCode.PROCESS_NOT_FOUND, "Just an error"))));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Just an error");
    expectedBody.setTitle(NOT_FOUND.name());
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
  void shouldYieldServiceUnavailableWhenBrokerErrorExhausted() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerError(
                    new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "Just an error"))));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Just an error");
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
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }

  @Test
  void shouldYieldUnavailableWhenBrokerErrorLeaderMismatch() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerError(
                    new BrokerError(ErrorCode.PARTITION_LEADER_MISMATCH, "Just an error"))));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Just an error");
    expectedBody.setTitle(UNAVAILABLE.name());
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
        "INVALID_MESSAGE_TEMPLATE",
        "INVALID_DEPLOYMENT_PARTITION",
        "SBE_UNKNOWN",
        "NULL_VAL"
      })
  public void shouldYieldInternalErrorWhenBrokerError(final ErrorCode errorCode) {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerError(new BrokerError(errorCode, "Just an error"))));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Unexpected error occurred between gateway and broker (code: "
                + errorCode
                + ") (message: Just an error)");
    expectedBody.setTitle(INTERNAL.name());
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

  @ParameterizedTest
  @EnumSource(
      value = RejectionType.class,
      names = {"PROCESSING_ERROR", "EXCEEDED_BATCH_RECORD_SIZE"})
  public void shouldYieldInternalErrorWhenRejectionInternal(final RejectionType rejectionType) {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        UserTaskIntent.COMPLETE, 1L, rejectionType, "Just an error"))));

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "status": 500,
              "title": "INTERNAL",
              "detail": "Command 'COMPLETE' rejected with code '%s': Just an error",
              "instance": "%s"
            }"""
            .formatted(rejectionType, "/v1/user-tasks/1/completion");

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    Mockito.verify(userTaskServices).completeUserTask(1L, Map.of(), "");
  }

  @ParameterizedTest
  @EnumSource(
      value = RejectionType.class,
      names = {"SBE_UNKNOWN", "NULL_VAL"})
  public void shouldYieldInternalErrorWhenRejectionUnknown(final RejectionType rejectionType) {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        UserTaskIntent.COMPLETE, 1L, rejectionType, "Just an error"))));

    final var expectedBody =
        """
            {
              "type": "about:blank",
              "status": 500,
              "title": "UNKNOWN",
              "detail": "Command 'COMPLETE' rejected with code '%s': Just an error",
              "instance": "%s"
            }"""
            .formatted(rejectionType, "/v1/user-tasks/1/completion");

    // when / then
    webClient
        .post()
        .uri(USER_TASKS_BASE_URL + "/1/completion")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);

    Mockito.verify(userTaskServices).completeUserTask(1L, Map.of(), "");
  }

  @Test
  public void shouldYieldInternalErrorWhenException() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapSearchError(
                    new CamundaSearchException(new NullPointerException("Just an error")))));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            NullPointerException.class.getName() + ": Just an error");
    expectedBody.setTitle("INTERNAL");
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
  public void shouldThrowExceptionWithRequestBodyMissing() {
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
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);

    Mockito.verifyNoInteractions(userTaskServices);
  }

  @Test
  public void shouldReturnGatewayTimeoutOnTimeoutException() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(new TimeoutException("Oh noes, timeouts!"))));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.GATEWAY_TIMEOUT,
            "Expected to handle request, but request timed out between gateway and broker");
    expectedBody.setTitle(DEADLINE_EXCEEDED.name());
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
  public void shouldReturnBadGatewayOnConnectionClosed() {
    // given
    final var errorMsg = "Oh noes, connection closed!";
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(ErrorMapper.mapError(new ConnectionClosed(errorMsg))));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_GATEWAY,
            "Expected to handle request, but the connection was cut prematurely with the broker; "
                + "the request may or may not have been accepted, and may not be safe to retry.");
    expectedBody.setTitle(ABORTED.name());
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
        .isEqualTo(HttpStatus.BAD_GATEWAY)
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
                ErrorMapper.mapError(
                    new ConnectTimeoutException("Oh noes, connection timeouts!"))));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Expected to handle request, but a connection timeout exception occurred");
    expectedBody.setTitle(UNAVAILABLE.name());
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
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(new ConnectException("Oh noes, connection timeouts!"))));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Expected to handle request, but there was a connection error with one of the brokers");
    expectedBody.setTitle(UNAVAILABLE.name());
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
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(new PartitionNotFoundException(1))));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Expected to handle request, but request could not be delivered");
    expectedBody.setTitle(UNAVAILABLE.name());
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
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(new MsgpackException("Oh noes, msg parsing!"))));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Expected to handle request, but messagepack property was invalid");
    expectedBody.setTitle(INVALID_ARGUMENT.name());
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
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(new JsonParseException("Oh noes, json parsing!"))));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Expected to handle request, but JSON property was invalid");
    expectedBody.setTitle(INVALID_ARGUMENT.name());
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
                ErrorMapper.mapError(new IllegalArgumentException("Oh noes, illegal arguments!"))));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Expected to handle request, but JSON property was invalid");
    expectedBody.setTitle(INVALID_ARGUMENT.name());
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
  public void shouldReturnServiceUnavailableOnRequestRetriesExhaustedException() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(new RequestRetriesExhaustedException())));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Expected to handle request, but all retries have been exhausted");
    expectedBody.setTitle(RESOURCE_EXHAUSTED.name());
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
  void shouldYieldUnavailableWhenPartitionPausesProcessing() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerError(
                    new BrokerError(ErrorCode.PARTITION_UNAVAILABLE, "Just an error"))));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Just an error");
    expectedBody.setTitle(UNAVAILABLE.name());
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

  @Test
  public void shouldYieldMaxMessageSizeExceededWhenRequestIsTooLarge() {
    // given
    Mockito.when(userTaskServices.completeUserTask(anyLong(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerError(
                    new BrokerError(ErrorCode.MALFORMED_REQUEST, "max size error"))));

    final var request = new UserTaskCompletionRequest();
    final var expectedBody =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "max size error");
    expectedBody.setTitle(INVALID_ARGUMENT.name());
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
        .isEqualTo(HttpStatus.BAD_REQUEST)
        .expectBody(ProblemDetail.class)
        .isEqualTo(expectedBody);
  }
}
