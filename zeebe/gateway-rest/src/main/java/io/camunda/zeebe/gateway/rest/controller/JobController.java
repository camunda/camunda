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
import io.camunda.gateway.protocol.model.CamundaProblemDetail;
import io.camunda.gateway.protocol.model.GlobalJobStatisticsQueryResult;
import io.camunda.gateway.protocol.model.JobActivationRequest;
import io.camunda.gateway.protocol.model.JobActivationResult;
import io.camunda.gateway.protocol.model.JobCompletionRequest;
import io.camunda.gateway.protocol.model.JobErrorRequest;
import io.camunda.gateway.protocol.model.JobErrorStatisticsQuery;
import io.camunda.gateway.protocol.model.JobErrorStatisticsQueryResult;
import io.camunda.gateway.protocol.model.JobFailRequest;
import io.camunda.gateway.protocol.model.JobSearchQuery;
import io.camunda.gateway.protocol.model.JobSearchQueryResult;
import io.camunda.gateway.protocol.model.JobTimeSeriesStatisticsQuery;
import io.camunda.gateway.protocol.model.JobTimeSeriesStatisticsQueryResult;
import io.camunda.gateway.protocol.model.JobTypeStatisticsQuery;
import io.camunda.gateway.protocol.model.JobTypeStatisticsQueryResult;
import io.camunda.gateway.protocol.model.JobUpdateRequest;
import io.camunda.gateway.protocol.model.JobWorkerStatisticsQuery;
import io.camunda.gateway.protocol.model.JobWorkerStatisticsQueryResult;
import io.camunda.search.query.JobQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.JobServices;
import io.camunda.service.JobServices.ActivateJobsRequest;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPatchMapping;
import io.camunda.zeebe.gateway.rest.annotation.CamundaPostMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.util.Either;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequestMapping("/v2/jobs")
public class JobController {

  private final ResponseObserverProvider responseObserverProvider;
  private final JobServices<JobActivationResult> jobServices;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final GatewayRestConfiguration gatewayRestConfiguration;

  public JobController(
      final JobServices<JobActivationResult> jobServices,
      final ResponseObserverProvider responseObserverProvider,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider,
      final GatewayRestConfiguration gatewayRestConfiguration) {
    this.jobServices = jobServices;
    this.responseObserverProvider = responseObserverProvider;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
    this.gatewayRestConfiguration = gatewayRestConfiguration;
  }

  @CamundaPostMapping(path = "/activation")
  public CompletableFuture<ResponseEntity<Object>> activateJobs(
      @RequestBody final JobActivationRequest activationRequest) {
    return RequestMapper.toJobsActivationRequest(
            activationRequest, multiTenancyCfg.isChecksEnabled())
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

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/search")
  public ResponseEntity<JobSearchQueryResult> searchJobs(
      @RequestBody(required = false) final JobSearchQuery request) {
    return SearchQueryRequestMapper.toJobQuery(request)
        .fold(RestErrorMapper::mapProblemToResponse, this::search);
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/statistics/global")
  public ResponseEntity<GlobalJobStatisticsQueryResult> getGlobalJobStatistics(
      @RequestParam final OffsetDateTime from,
      @RequestParam final OffsetDateTime to,
      @RequestParam(required = false) final String jobType,
      final HttpServletRequest request) {
    return requireJobMetricsEnabled(request.getRequestURI())
        .flatMap(ok -> SearchQueryRequestMapper.toGlobalJobStatisticsQuery(from, to, jobType))
        .fold(RestErrorMapper::mapProblemToResponse, this::getGlobalStatistics);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/statistics/by-types")
  public ResponseEntity<JobTypeStatisticsQueryResult> getJobTypeStatistics(
      @RequestBody final JobTypeStatisticsQuery request, final HttpServletRequest httpRequest) {
    return requireJobMetricsEnabled(httpRequest.getRequestURI())
        .flatMap(ok -> SearchQueryRequestMapper.toJobTypeStatisticsQuery(request))
        .fold(RestErrorMapper::mapProblemToResponse, this::getTypeStatistics);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/statistics/by-workers")
  public ResponseEntity<JobWorkerStatisticsQueryResult> getJobWorkerStatistics(
      @RequestBody final JobWorkerStatisticsQuery request, final HttpServletRequest httpRequest) {
    return requireJobMetricsEnabled(httpRequest.getRequestURI())
        .flatMap(ok -> SearchQueryRequestMapper.toJobWorkerStatisticsQuery(request))
        .fold(RestErrorMapper::mapProblemToResponse, this::getWorkerStatistics);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/statistics/time-series")
  public ResponseEntity<JobTimeSeriesStatisticsQueryResult> getJobTimeSeriesStatistics(
      @RequestBody final JobTimeSeriesStatisticsQuery request,
      final HttpServletRequest httpRequest) {
    return requireJobMetricsEnabled(httpRequest.getRequestURI())
        .flatMap(ok -> SearchQueryRequestMapper.toJobTimeSeriesStatisticsQuery(request))
        .fold(RestErrorMapper::mapProblemToResponse, this::getTimeSeriesStatistics);
  }

  @RequiresSecondaryStorage
  @CamundaPostMapping(path = "/statistics/errors")
  public ResponseEntity<JobErrorStatisticsQueryResult> getJobErrorStatistics(
      @RequestBody final JobErrorStatisticsQuery request) {
    return SearchQueryRequestMapper.toJobErrorStatisticsQuery(request)
        .fold(RestErrorMapper::mapProblemToResponse, this::getErrorStatistics);
  }

  private CompletableFuture<ResponseEntity<Object>> activateJobs(
      final ActivateJobsRequest activationRequest) {
    final var result = new CompletableFuture<ResponseEntity<Object>>();
    final var responseObserver = responseObserverProvider.apply(result);
    jobServices.activateJobs(
        activationRequest,
        responseObserver,
        responseObserver::setCancelationHandler,
        authenticationProvider.getCamundaAuthentication());
    return result.handleAsync(
        (res, ex) -> {
          responseObserver.invokeCancelationHandler();
          return res;
        });
  }

  private CompletableFuture<ResponseEntity<Object>> failJob(final FailJobRequest failJobRequest) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            jobServices.failJob(
                failJobRequest.jobKey(),
                failJobRequest.retries(),
                failJobRequest.errorMessage(),
                failJobRequest.retryBackoff(),
                failJobRequest.variables(),
                authenticationProvider.getCamundaAuthentication()));
  }

  private CompletableFuture<ResponseEntity<Object>> errorJob(
      final ErrorJobRequest errorJobRequest) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            jobServices.errorJob(
                errorJobRequest.jobKey(),
                errorJobRequest.errorCode(),
                errorJobRequest.errorMessage(),
                errorJobRequest.variables(),
                authenticationProvider.getCamundaAuthentication()));
  }

  private CompletableFuture<ResponseEntity<Object>> completeJob(
      final CompleteJobRequest completeJobRequest) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            jobServices.completeJob(
                completeJobRequest.jobKey(),
                completeJobRequest.variables(),
                completeJobRequest.result(),
                authenticationProvider.getCamundaAuthentication()));
  }

  private CompletableFuture<ResponseEntity<Object>> updateJob(
      final UpdateJobRequest updateJobRequest) {
    return RequestExecutor.executeServiceMethodWithNoContentResult(
        () ->
            jobServices.updateJob(
                updateJobRequest.jobKey(),
                updateJobRequest.operationReference(),
                updateJobRequest.changeset(),
                authenticationProvider.getCamundaAuthentication()));
  }

  private ResponseEntity<JobSearchQueryResult> search(final JobQuery query) {
    try {
      final var result =
          jobServices.search(query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(SearchQueryResponseMapper.toJobSearchQueryResponse(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<GlobalJobStatisticsQueryResult> getGlobalStatistics(
      final io.camunda.search.query.GlobalJobStatisticsQuery query) {
    try {
      final var result =
          jobServices.getGlobalStatistics(query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(SearchQueryResponseMapper.toGlobalJobStatisticsQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<JobTypeStatisticsQueryResult> getTypeStatistics(
      final io.camunda.search.query.JobTypeStatisticsQuery query) {
    try {
      final var result =
          jobServices.getJobTypeStatistics(
              query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(SearchQueryResponseMapper.toJobTypeStatisticsQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<JobWorkerStatisticsQueryResult> getWorkerStatistics(
      final io.camunda.search.query.JobWorkerStatisticsQuery query) {
    try {
      final var result =
          jobServices.getJobWorkerStatistics(
              query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(SearchQueryResponseMapper.toJobWorkerStatisticsQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<JobTimeSeriesStatisticsQueryResult> getTimeSeriesStatistics(
      final io.camunda.search.query.JobTimeSeriesStatisticsQuery query) {
    try {
      final var result =
          jobServices.getJobTimeSeriesStatistics(
              query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toJobTimeSeriesStatisticsQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private ResponseEntity<JobErrorStatisticsQueryResult> getErrorStatistics(
      final io.camunda.search.query.JobErrorStatisticsQuery query) {
    try {
      final var result =
          jobServices.getJobErrorStatistics(
              query, authenticationProvider.getCamundaAuthentication());
      return ResponseEntity.ok(SearchQueryResponseMapper.toJobErrorStatisticsQueryResult(result));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }

  private Either<ProblemDetail, Void> requireJobMetricsEnabled(final String requestUri) {
    if (!gatewayRestConfiguration.getJobMetrics().isEnabled()) {
      final var problemDetail =
          CamundaProblemDetail.forStatusAndDetail(
              HttpStatus.FORBIDDEN, "Job metrics feature is disabled");
      problemDetail.setTitle("FORBIDDEN");
      problemDetail.setInstance(URI.create(requestUri));
      return Either.left(problemDetail);
    }
    return Either.right(null);
  }
}
