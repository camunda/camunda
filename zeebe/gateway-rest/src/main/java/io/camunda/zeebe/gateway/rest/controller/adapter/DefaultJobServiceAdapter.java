/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.JobActivation;
import io.camunda.gateway.protocol.model.JobActivationRequest;
import io.camunda.gateway.protocol.model.JobCompletionRequest;
import io.camunda.gateway.protocol.model.JobErrorRequest;
import io.camunda.gateway.protocol.model.JobErrorStatisticsQuery;
import io.camunda.gateway.protocol.model.JobFailRequest;
import io.camunda.gateway.protocol.model.JobSearchQuery;
import io.camunda.gateway.protocol.model.JobTimeSeriesStatisticsQuery;
import io.camunda.gateway.protocol.model.JobTypeStatisticsQuery;
import io.camunda.gateway.protocol.model.JobUpdateRequest;
import io.camunda.gateway.protocol.model.JobWorkerStatisticsQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.JobServices;
import io.camunda.zeebe.gateway.rest.CamundaProblemDetail;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.controller.ResponseObserverProvider;
import io.camunda.zeebe.gateway.rest.controller.generated.JobServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RequestExecutor;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import io.camunda.zeebe.util.Either;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultJobServiceAdapter implements JobServiceAdapter {

  private final JobServices<JobActivation> jobServices;
  private final GatewayRestConfiguration gatewayRestConfiguration;
  private final ResponseObserverProvider responseObserverProvider;
  private final MultiTenancyConfiguration multiTenancyCfg;

  public DefaultJobServiceAdapter(
      final JobServices<JobActivation> jobServices,
      final GatewayRestConfiguration gatewayRestConfiguration,
      final ResponseObserverProvider responseObserverProvider,
      final MultiTenancyConfiguration multiTenancyCfg) {
    this.jobServices = jobServices;
    this.gatewayRestConfiguration = gatewayRestConfiguration;
    this.responseObserverProvider = responseObserverProvider;
    this.multiTenancyCfg = multiTenancyCfg;
  }

  @SuppressWarnings("unchecked")
  @Override
  public ResponseEntity<Object> activateJobs(
      final JobActivationRequest jobActivationRequestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toJobsActivationRequest(
            jobActivationRequestStrict, multiTenancyCfg.isChecksEnabled())
        .fold(
            RestErrorMapper::mapProblemToResponse,
            activationRequest -> {
              final var result = new CompletableFuture<ResponseEntity<Object>>();
              final var responseObserver = responseObserverProvider.apply(result);
              jobServices.activateJobs(
                  activationRequest,
                  responseObserver,
                  responseObserver::setCancelationHandler,
                  authentication);
              return (ResponseEntity)
                  result
                      .handleAsync(
                          (res, ex) -> {
                            responseObserver.invokeCancelationHandler();
                            return res;
                          })
                      .join();
            });
  }

  @Override
  public ResponseEntity<Object> getGlobalJobStatistics(
      final String from,
      final String to,
      final String jobType,
      final CamundaAuthentication authentication) {
    return requireJobMetricsEnabled("/v2/jobs/statistics/global")
        .flatMap(
            ok -> {
              if (from == null || to == null) {
                final var problem =
                    CamundaProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST,
                        "Both 'from' and 'to' query parameters are required.");
                return Either.left(problem);
              }
              return SearchQueryRequestMapper.toGlobalJobStatisticsQuery(
                  OffsetDateTime.parse(from), OffsetDateTime.parse(to), jobType);
            })
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = jobServices.getGlobalStatistics(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toGlobalJobStatisticsQueryResult(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getJobTypeStatistics(
      final JobTypeStatisticsQuery requestStrict, final CamundaAuthentication authentication) {
    return requireJobMetricsEnabled("/v2/jobs/statistics/by-types")
        .flatMap(ok -> SearchQueryRequestMapper.toJobTypeStatisticsQuery(requestStrict))
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = jobServices.getJobTypeStatistics(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toJobTypeStatisticsQueryResult(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getJobWorkerStatistics(
      final JobWorkerStatisticsQuery requestStrict, final CamundaAuthentication authentication) {
    return requireJobMetricsEnabled("/v2/jobs/statistics/by-workers")
        .flatMap(ok -> SearchQueryRequestMapper.toJobWorkerStatisticsQuery(requestStrict))
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = jobServices.getJobWorkerStatistics(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toJobWorkerStatisticsQueryResult(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getJobTimeSeriesStatistics(
      final JobTimeSeriesStatisticsQuery requestStrict,
      final CamundaAuthentication authentication) {
    return requireJobMetricsEnabled("/v2/jobs/statistics/time-series")
        .flatMap(ok -> SearchQueryRequestMapper.toJobTimeSeriesStatisticsQuery(requestStrict))
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = jobServices.getJobTimeSeriesStatistics(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toJobTimeSeriesStatisticsQueryResult(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getJobErrorStatistics(
      final JobErrorStatisticsQuery requestStrict, final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toJobErrorStatisticsQuery(requestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = jobServices.getJobErrorStatistics(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toJobErrorStatisticsQueryResult(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> searchJobs(
      final JobSearchQuery requestStrict, final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toJobQueryStrict(requestStrict)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                final var result = jobServices.search(query, authentication);
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toJobSearchQueryResponse(result));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Void> failJob(
      final Long jobKey,
      final JobFailRequest failRequestStrict,
      final CamundaAuthentication authentication) {
    final var mapped = RequestMapper.toJobFailRequest(failRequestStrict, jobKey);
    return RequestExecutor.executeSync(
        () ->
            jobServices.failJob(
                mapped.jobKey(),
                mapped.retries(),
                mapped.errorMessage(),
                mapped.retryBackoff(),
                mapped.variables(),
                authentication));
  }

  @Override
  public ResponseEntity<Void> throwJobError(
      final Long jobKey,
      final JobErrorRequest errorRequestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toJobErrorRequest(errorRequestStrict, jobKey)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                RequestExecutor.executeSync(
                    () ->
                        jobServices.errorJob(
                            mapped.jobKey(),
                            mapped.errorCode(),
                            mapped.errorMessage(),
                            mapped.variables(),
                            authentication)));
  }

  @Override
  public ResponseEntity<Void> completeJob(
      final Long jobKey,
      final JobCompletionRequest completionRequestStrict,
      final CamundaAuthentication authentication) {
    final var mapped = RequestMapper.toJobCompletionRequest(completionRequestStrict, jobKey);
    return RequestExecutor.executeSync(
        () ->
            jobServices.completeJob(
                mapped.jobKey(), mapped.variables(), mapped.result(), authentication));
  }

  @Override
  public ResponseEntity<Void> updateJob(
      final Long jobKey,
      final JobUpdateRequest updateRequestStrict,
      final CamundaAuthentication authentication) {
    return RequestMapper.toJobUpdateRequest(updateRequestStrict, jobKey)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            mapped ->
                RequestExecutor.executeSync(
                    () ->
                        jobServices.updateJob(
                            mapped.jobKey(),
                            mapped.operationReference(),
                            mapped.changeset(),
                            authentication)));
  }

  private Either<ProblemDetail, Void> requireJobMetricsEnabled(final String requestUri) {
    if (!gatewayRestConfiguration.getJobMetrics().isEnabled()) {
      final var problemDetail =
          CamundaProblemDetail.forStatusAndDetail(
              HttpStatus.FORBIDDEN, "Job metrics feature is disabled");
      problemDetail.setInstance(URI.create(requestUri));
      return Either.left(problemDetail);
    }
    return Either.right(null);
  }
}
