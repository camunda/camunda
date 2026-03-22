/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.annotation.Generated;
import org.springframework.http.ResponseEntity;

/**
 * Service adapter for Job operations. Implements request mapping, service delegation, and response
 * construction.
 */
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public interface JobServiceAdapter {

  ResponseEntity<Object> getGlobalJobStatistics(
      String from, String to, String jobType, CamundaAuthentication authentication);

  ResponseEntity<Object> getJobTypeStatistics(
      GeneratedJobTypeStatisticsQueryStrictContract jobTypeStatisticsQuery,
      CamundaAuthentication authentication);

  ResponseEntity<Object> getJobWorkerStatistics(
      GeneratedJobWorkerStatisticsQueryStrictContract jobWorkerStatisticsQuery,
      CamundaAuthentication authentication);

  ResponseEntity<Object> getJobTimeSeriesStatistics(
      GeneratedJobTimeSeriesStatisticsQueryStrictContract jobTimeSeriesStatisticsQuery,
      CamundaAuthentication authentication);

  ResponseEntity<Object> getJobErrorStatistics(
      GeneratedJobErrorStatisticsQueryStrictContract jobErrorStatisticsQuery,
      CamundaAuthentication authentication);

  ResponseEntity<Object> activateJobs(
      GeneratedJobActivationRequestStrictContract jobActivationRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Object> searchJobs(
      GeneratedJobSearchQueryRequestStrictContract jobSearchQuery,
      CamundaAuthentication authentication);

  ResponseEntity<Void> failJob(
      Long jobKey,
      GeneratedJobFailRequestStrictContract jobFailRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Void> throwJobError(
      Long jobKey,
      GeneratedJobErrorRequestStrictContract jobErrorRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Void> completeJob(
      Long jobKey,
      GeneratedJobCompletionRequestStrictContract jobCompletionRequest,
      CamundaAuthentication authentication);

  ResponseEntity<Void> updateJob(
      Long jobKey,
      GeneratedJobUpdateRequestStrictContract jobUpdateRequest,
      CamundaAuthentication authentication);
}
