/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.protocol.record.RejectionType.INVALID_ARGUMENT;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.protocol.rest.UserTaskCompletionRequest;
import io.camunda.zeebe.gateway.rest.impl.broker.request.BrokerUserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.rest.impl.broker.request.BrokerUserTaskCompletionRequest;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ServerWebExchange;

@ZeebeRestController
public class UserTaskController {

  private final BrokerClient brokerClient;

  @Autowired
  public UserTaskController(final BrokerClient brokerClient) {
    this.brokerClient = brokerClient;
  }

  @PostMapping(
      path = "/user-tasks/{userTaskKey}/completion",
      produces = "application/json",
      consumes = "application/json")
  public CompletableFuture<ResponseEntity<Object>> completeUserTask(
      final ServerWebExchange context,
      @PathVariable final long userTaskKey,
      @RequestBody(required = false) final UserTaskCompletionRequest completionRequest) {

    final BrokerUserTaskCompletionRequest brokerRequest =
        RequestMapper.toUserTaskCompletionRequest(completionRequest, userTaskKey, context);

    final CompletableFuture<BrokerResponse<UserTaskRecord>> brokerResponse =
        brokerClient.sendRequest(brokerRequest);

    return brokerResponse.handleAsync(
        (response, error) ->
            RestErrorMapper.getResponse(response, error, UserTaskController::mapRejectionToProblem)
                .orElseGet(() -> ResponseEntity.noContent().build()));
  }

  @PostMapping(
      path = "/user-tasks/{userTaskKey}/assignment",
      produces = "application/json",
      consumes = "application/json")
  public CompletableFuture<ResponseEntity<Object>> assignUserTask(
      final ServerWebExchange context,
      @PathVariable final long userTaskKey,
      @RequestBody final UserTaskAssignmentRequest assignmentRequest) {

    final Optional<ResponseEntity<Object>> validationErrorResponse =
        validateAssignmentRequest(assignmentRequest);

    if (validationErrorResponse.isPresent()) {
      return CompletableFuture.completedFuture(validationErrorResponse.get());
    }

    final BrokerUserTaskAssignmentRequest brokerRequest =
        RequestMapper.toUserTaskAssignmentRequest(assignmentRequest, userTaskKey, context);

    final CompletableFuture<BrokerResponse<UserTaskRecord>> brokerResponse =
        brokerClient.sendRequest(brokerRequest);

    return brokerResponse.handleAsync(
        (response, error) ->
            RestErrorMapper.getResponse(response, error, UserTaskController::mapRejectionToProblem)
                .orElseGet(() -> ResponseEntity.noContent().build()));
  }

  private static ProblemDetail mapRejectionToProblem(final BrokerRejection rejection) {
    final String message =
        String.format(
            "Command '%s' rejected with code '%s': %s",
            rejection.intent(), rejection.type(), rejection.reason());
    final String title = rejection.type().name();
    return switch (rejection.type()) {
      case NOT_FOUND:
        yield RestErrorMapper.createProblemDetail(HttpStatus.NOT_FOUND, message, title);
      case INVALID_STATE:
        yield RestErrorMapper.createProblemDetail(HttpStatus.CONFLICT, message, title);
      case INVALID_ARGUMENT:
      case ALREADY_EXISTS:
        yield RestErrorMapper.createProblemDetail(HttpStatus.BAD_REQUEST, message, title);
      default:
        {
          yield RestErrorMapper.createProblemDetail(
              HttpStatus.INTERNAL_SERVER_ERROR, message, title);
        }
    };
  }

  private static Optional<ResponseEntity<Object>> validateAssignmentRequest(
      final UserTaskAssignmentRequest assignmentRequest) {
    if (assignmentRequest.getAssignee() == null || assignmentRequest.getAssignee().isBlank()) {
      final String message = "No assignee provided";
      final ProblemDetail problemDetail =
          RestErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST, message, INVALID_ARGUMENT.name());
      return Optional.of(ResponseEntity.of(problemDetail).build());
    }
    return Optional.empty();
  }
}
