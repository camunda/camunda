/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.impl.job.ActivateJobsHandler;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResponse;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@ZeebeRestController
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
            RestErrorMapper::mapProblemToCompletedResponse);
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
}
