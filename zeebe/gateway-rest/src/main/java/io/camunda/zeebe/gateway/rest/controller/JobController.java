/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.JobServices;
import io.camunda.service.JobServices.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobActivationResult;
import io.camunda.zeebe.gateway.protocol.rest.JobCompletionRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobErrorRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobFailRequest;
import io.camunda.zeebe.gateway.protocol.rest.JobUpdateRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper;
import io.camunda.zeebe.gateway.rest.RequestMapper.CompleteJobRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper.ErrorJobRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper.FailJobRequest;
import io.camunda.zeebe.gateway.rest.RequestMapper.UpdateJobRequest;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPatchMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@CamundaRestController
@RequestMapping("/v2/jobs")
public class JobController {

  private final ResponseObserverProvider responseObserverProvider;
  private final JobServices<JobActivationResult> jobServices;
  private final MultiTenancyConfiguration multiTenancyCfg;

  public JobController(
      final JobServices<JobActivationResult> jobServices,
      final ResponseObserverProvider responseObserverProvider,
      final MultiTenancyConfiguration multiTenancyCfg) {
    this.jobServices = jobServices;
    this.responseObserverProvider = responseObserverProvider;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @CamundaPostMapping(path = "/activation")
  public CompletableFuture<ResponseEntity<Object>> activateJobs(
      @RequestBody final JobActivationRequest activationRequest) {
    return RequestMapper.toJobsActivationRequest(activationRequest, multiTenancyCfg.isEnabled())
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::activateJobs);
  }

  @CamundaPostMapping(path = "/{jobKey}/failure")
  public CompletableFuture<ResponseEntity<Object>> failureJob(
      @PathVariable final long jobKey,
      @RequestBody(required = false) final JobFailRequest failureRequest) {
    return failJob(RequestMapper.toJobFailRequest(failureRequest, jobKey));
  }

  @CamundaPostMapping(path = "/{jobKey}/error")
  public CompletableFuture<ResponseEntity<Object>> errorJob(
      @PathVariable final long jobKey, @RequestBody final JobErrorRequest errorRequest) {
    return RequestMapper.toJobErrorRequest(errorRequest, jobKey)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::errorJob);
  }

  @CamundaPostMapping(path = "/{jobKey}/completion")
  public CompletableFuture<ResponseEntity<Object>> completeJob(
      @PathVariable final long jobKey,
      @RequestBody(required = false) final JobCompletionRequest completionRequest) {
    return completeJob(RequestMapper.toJobCompletionRequest(completionRequest, jobKey));
  }

  @CamundaPatchMapping(path = "/{jobKey}")
  public CompletableFuture<ResponseEntity<Object>> updateJob(
      @PathVariable final long jobKey, @RequestBody final JobUpdateRequest jobUpdateRequest) {
    return RequestMapper.toJobUpdateRequest(jobUpdateRequest, jobKey)
        .fold(RestErrorMapper::mapProblemToCompletedResponse, this::updateJob);
  }

  private CompletableFuture<ResponseEntity<Object>> activateJobs(
      final ActivateJobsRequest activationRequest) {
    final var result = new CompletableFuture<ResponseEntity<Object>>();
    final var responseObserver = responseObserverProvider.apply(result);
    jobServices
        .withAuthentication(RequestMapper.getAuthentication())
        .activateJobs(activationRequest, responseObserver, responseObserver::setCancelationHandler);
    return result.handleAsync(
        (res, ex) -> {
          responseObserver.invokeCancelationHandler();
          return res;
        });
  }

  private CompletableFuture<ResponseEntity<Object>> failJob(final FailJobRequest failJobRequest) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            jobServices
                .withAuthentication(RequestMapper.getAuthentication())
                .failJob(
                    failJobRequest.jobKey(),
                    failJobRequest.retries(),
                    failJobRequest.errorMessage(),
                    failJobRequest.retryBackoff(),
                    failJobRequest.variables()));
  }

  private CompletableFuture<ResponseEntity<Object>> errorJob(
      final ErrorJobRequest errorJobRequest) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            jobServices
                .withAuthentication(RequestMapper.getAuthentication())
                .errorJob(
                    errorJobRequest.jobKey(),
                    errorJobRequest.errorCode(),
                    errorJobRequest.errorMessage(),
                    errorJobRequest.variables()));
  }

  private CompletableFuture<ResponseEntity<Object>> completeJob(
      final CompleteJobRequest completeJobRequest) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            jobServices
                .withAuthentication(RequestMapper.getAuthentication())
                .completeJob(
                    completeJobRequest.jobKey(),
                    completeJobRequest.variables(),
                    completeJobRequest.result()));
  }

  private CompletableFuture<ResponseEntity<Object>> updateJob(
      final UpdateJobRequest updateJobRequest) {
    return RequestMapper.executeServiceMethodWithNoContentResult(
        () ->
            jobServices
                .withAuthentication(RequestMapper.getAuthentication())
                .updateJob(updateJobRequest.jobKey(), updateJobRequest.changeset()));
  }
}
