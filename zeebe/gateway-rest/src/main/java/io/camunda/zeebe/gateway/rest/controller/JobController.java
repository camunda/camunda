/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.RequestMapper.CompleteJobRequest;
import io.camunda.gateway.mapping.http.RequestMapper.ErrorJobRequest;
import io.camunda.gateway.mapping.http.RequestMapper.FailJobRequest;
import io.camunda.gateway.mapping.http.RequestMapper.UpdateJobRequest;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.JobActivationRequest;
import io.camunda.gateway.protocol.model.JobActivationResult;
import io.camunda.gateway.protocol.model.JobCompletionRequest;
import io.camunda.gateway.protocol.model.JobErrorRequest;
import io.camunda.gateway.protocol.model.JobFailRequest;
import io.camunda.gateway.protocol.model.JobSearchQuery;
import io.camunda.gateway.protocol.model.JobSearchQueryResult;
import io.camunda.gateway.protocol.model.JobUpdateRequest;
import io.camunda.search.query.JobQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.JobServices;
import io.camunda.service.JobServices.ActivateJobsRequest;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPatchMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@CamundaRestController("/v2/jobs")
public class JobController {

  private final ResponseObserverProvider responseObserverProvider;
  private final JobServices<JobActivationResult> jobServices;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;

  public JobController(
      final JobServices<JobActivationResult> jobServices,
      final ResponseObserverProvider responseObserverProvider,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.jobServices = jobServices;
    this.responseObserverProvider = responseObserverProvider;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaPostMapping(path = "/activation")
  public CompletableFuture<ResponseEntity<Object>> activateJobs(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @RequestBody final JobActivationRequest activationRequest) {
    return RequestMapper.toJobsActivationRequest(
            activationRequest, multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            (request) -> activateJobs(request, engineName));
  }

  @CamundaPostMapping(path = "/{jobKey}/failure")
  public CompletableFuture<ResponseEntity<Object>> failureJob(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @PathVariable final long jobKey,
      @RequestBody(required = false) final JobFailRequest failureRequest) {
    return failJob(RequestMapper.toJobFailRequest(failureRequest, jobKey), engineName);
  }

  @CamundaPostMapping(path = "/{jobKey}/error")
  public CompletableFuture<ResponseEntity<Object>> errorJob(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @PathVariable final long jobKey,
      @RequestBody final JobErrorRequest errorRequest) {
    return RequestMapper.toJobErrorRequest(errorRequest, jobKey)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            (request) -> errorJob(request, engineName));
  }

  @CamundaPostMapping(path = "/{jobKey}/completion")
  public CompletableFuture<ResponseEntity<Object>> completeJob(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @PathVariable final long jobKey,
      @RequestBody(required = false) final JobCompletionRequest completionRequest) {
    return completeJob(RequestMapper.toJobCompletionRequest(completionRequest, jobKey), engineName);
  }

  @CamundaPatchMapping(path = "/{jobKey}")
  public CompletableFuture<ResponseEntity<Object>> updateJob(
      @PathVariable(name = "engineName", required = false) final String engineName,
      @PathVariable final long jobKey,
      @RequestBody final JobUpdateRequest jobUpdateRequest) {
    return RequestMapper.toJobUpdateRequest(jobUpdateRequest, jobKey)
        .fold(
            RestErrorMapper::mapProblemToCompletedResponse,
            (request) -> updateJob(request, engineName));
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<JobSearchQueryResult> searchJobs(
      @RequestBody(required = false) final JobSearchQuery request) {
    return SearchQueryRequestMapper.toJobQuery(request)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  private CompletableFuture<ResponseEntity<Object>> activateJobs(
      final ActivateJobsRequest activationRequest, final String engineName) {
    final var result = new CompletableFuture<ResponseEntity<Object>>();
    final var responseObserver = responseObserverProvider.apply(result);
    jobServices
        .withEngineName(engineName)
        .withAuthentication(authenticationProvider.getCamundaAuthentication())
        .activateJobs(activationRequest, responseObserver, responseObserver::setCancelationHandler);
    return result.handleAsync(
        (res, ex) -> {
          responseObserver.invokeCancelationHandler();
          return res;
        });
  }

  private CompletableFuture<ResponseEntity<Object>> failJob(
      final FailJobRequest failJobRequest, final String engineName) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            jobServices
                .withEngineName(engineName)
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .failJob(
                    failJobRequest.jobKey(),
                    failJobRequest.retries(),
                    failJobRequest.errorMessage(),
                    failJobRequest.retryBackoff(),
                    failJobRequest.variables()));
  }

  private CompletableFuture<ResponseEntity<Object>> errorJob(
      final ErrorJobRequest errorJobRequest, final String engineName) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            jobServices
                .withEngineName(engineName)
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .errorJob(
                    errorJobRequest.jobKey(),
                    errorJobRequest.errorCode(),
                    errorJobRequest.errorMessage(),
                    errorJobRequest.variables()));
  }

  private CompletableFuture<ResponseEntity<Object>> completeJob(
      final CompleteJobRequest completeJobRequest, final String engineName) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            jobServices
                .withEngineName(engineName)
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .completeJob(
                    completeJobRequest.jobKey(),
                    completeJobRequest.variables(),
                    completeJobRequest.result()));
  }

  private CompletableFuture<ResponseEntity<Object>> updateJob(
      final UpdateJobRequest updateJobRequest, final String engineName) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            jobServices
                .withEngineName(engineName)
                .withAuthentication(authenticationProvider.getCamundaAuthentication())
                .updateJob(
                    updateJobRequest.jobKey(),
                    updateJobRequest.operationReference(),
                    updateJobRequest.changeset()));
  }

  private ResponseEntity<JobSearchQueryResult> search(final JobQuery query) {
    try {
      final var result =
          jobServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(SearchQueryResponseMapper.toJobSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
