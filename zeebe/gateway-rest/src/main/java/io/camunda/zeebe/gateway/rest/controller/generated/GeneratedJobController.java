/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 */
package io.camunda.zeebe.gateway.rest.controller.generated;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobActivationRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobCompletionRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobErrorRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobErrorStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobFailRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobTimeSeriesStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobTypeStatisticsQueryStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobUpdateRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobWorkerStatisticsQueryStrictContract;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequestMapping("/v2")
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public class GeneratedJobController {

  private final JobServiceAdapter serviceAdapter;
  private final CamundaAuthenticationProvider authenticationProvider;

  public GeneratedJobController(
      final JobServiceAdapter serviceAdapter,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.serviceAdapter = serviceAdapter;
    this.authenticationProvider = authenticationProvider;
  }

  @RequestMapping(
      method = RequestMethod.GET,
      value = "/jobs/statistics/global",
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> getGlobalJobStatistics(
      @RequestParam(name = "from", required = false) final String from,
      @RequestParam(name = "to", required = false) final String to,
      @RequestParam(name = "jobType", required = false) final String jobType
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getGlobalJobStatistics(from, to, jobType, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/jobs/statistics/by-types",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> getJobTypeStatistics(
      @RequestBody final GeneratedJobTypeStatisticsQueryStrictContract jobTypeStatisticsQuery
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getJobTypeStatistics(jobTypeStatisticsQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/jobs/statistics/by-workers",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> getJobWorkerStatistics(
      @RequestBody final GeneratedJobWorkerStatisticsQueryStrictContract jobWorkerStatisticsQuery
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getJobWorkerStatistics(jobWorkerStatisticsQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/jobs/statistics/time-series",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> getJobTimeSeriesStatistics(
      @RequestBody final GeneratedJobTimeSeriesStatisticsQueryStrictContract jobTimeSeriesStatisticsQuery
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getJobTimeSeriesStatistics(jobTimeSeriesStatisticsQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/jobs/statistics/errors",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> getJobErrorStatistics(
      @RequestBody final GeneratedJobErrorStatisticsQueryStrictContract jobErrorStatisticsQuery
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.getJobErrorStatistics(jobErrorStatisticsQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/jobs/activation",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> activateJobs(
      @RequestBody final GeneratedJobActivationRequestStrictContract jobActivationRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.activateJobs(jobActivationRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/jobs/search",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Object> searchJobs(
      @RequestBody(required = false) final GeneratedJobSearchQueryRequestStrictContract jobSearchQuery
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.searchJobs(jobSearchQuery, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/jobs/{jobKey}/failure",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> failJob(
      @PathVariable("jobKey") final Long jobKey,
      @RequestBody(required = false) final GeneratedJobFailRequestStrictContract jobFailRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.failJob(jobKey, jobFailRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/jobs/{jobKey}/error",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> throwJobError(
      @PathVariable("jobKey") final Long jobKey,
      @RequestBody final GeneratedJobErrorRequestStrictContract jobErrorRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.throwJobError(jobKey, jobErrorRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.POST,
      value = "/jobs/{jobKey}/completion",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> completeJob(
      @PathVariable("jobKey") final Long jobKey,
      @RequestBody(required = false) final GeneratedJobCompletionRequestStrictContract jobCompletionRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.completeJob(jobKey, jobCompletionRequest, authentication);
  }

  @RequestMapping(
      method = RequestMethod.PATCH,
      value = "/jobs/{jobKey}",
      consumes = { "application/json" },
      produces = { "application/json", "application/problem+json" })
  public ResponseEntity<Void> updateJob(
      @PathVariable("jobKey") final Long jobKey,
      @RequestBody final GeneratedJobUpdateRequestStrictContract jobUpdateRequest
  ) {
    final var authentication =
        authenticationProvider.getAnonymousIfUnavailable();
    return serviceAdapter.updateJob(jobKey, jobUpdateRequest, authentication);
  }
}
