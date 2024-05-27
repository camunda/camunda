/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@ZeebeRestController
@RequestMapping("/v2")
public class JobController {

  private final ActivateJobsHandler<JobActivationResponse> activateJobsHandler;
  private final ResponseObserverProvider responseObserverProvider;

  @Autowired
  public JobController(
      final ActivateJobsHandler<JobActivationResponse> activateJobsHandler,
      final ResponseObserverProvider responseObserverProvider) {
    this.activateJobsHandler = activateJobsHandler;
    this.responseObserverProvider = responseObserverProvider;
  }

  @PostMapping(
      path = "/jobs/activation",
      produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_PROBLEM_JSON_VALUE},
      consumes = MediaType.APPLICATION_JSON_VALUE)
  public CompletableFuture<ResponseEntity<Object>> activateJobs(
      @RequestBody final JobActivationRequest activationRequest) {
    return RequestMapper.toJobsActivationRequest(activationRequest)
        .fold(
            brokerRequest -> sendBrokerRequest(brokerRequest, activationRequest),
            JobController::handleRequestMappingError);
  }

  private CompletableFuture<ResponseEntity<Object>> sendBrokerRequest(
      final BrokerActivateJobsRequest brokerRequest, final JobActivationRequest activationRequest) {
    final var result = new CompletableFuture<ResponseEntity<Object>>();
    final var responseObserver = responseObserverProvider.apply(result);
    activateJobsHandler.activateJobs(
        brokerRequest,
        responseObserver,
        responseObserver::setCancelationHandler,
        activationRequest.getRequestTimeout() == null ? 0 : activationRequest.getRequestTimeout());
    return result.handleAsync(
        (res, ex) -> {
          responseObserver.invokeCancelationHandler();
          return res;
        });
  }

  private static CompletableFuture<ResponseEntity<Object>> handleRequestMappingError(
      final ProblemDetail problemDetail) {
    return CompletableFuture.completedFuture(RestErrorMapper.mapProblemToResponse(problemDetail));
  }

  public static ProblemDetail mapRejectionToProblem(final BrokerRejection rejection) {
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
}
