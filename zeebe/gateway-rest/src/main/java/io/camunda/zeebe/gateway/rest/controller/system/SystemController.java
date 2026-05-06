/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.system;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.JobMetricsConfigurationResponse;
import io.camunda.gateway.protocol.model.SystemConfigurationResponse;
import io.camunda.gateway.protocol.model.UsageMetricsResponse;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.UsageMetricsServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration.JobMetricsConfiguration;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequestMapping("/v2/system")
public class SystemController {

  private final UsageMetricsServices usageMetricsServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final GatewayRestConfiguration gatewayRestConfiguration;

  public SystemController(
      final UsageMetricsServices usageMetricsServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final GatewayRestConfiguration gatewayRestConfiguration) {
    this.usageMetricsServices = usageMetricsServices;
    this.authenticationProvider = authenticationProvider;
    this.gatewayRestConfiguration = gatewayRestConfiguration;
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/usage-metrics")
  public ResponseEntity<UsageMetricsResponse> getUsageMetrics(
      @RequestParam final String startTime,
      @RequestParam final String endTime,
      @RequestParam(required = false) final String tenantId,
      @RequestParam(required = false, defaultValue = "false") final boolean withTenants) {

    return SearchQueryRequestMapper.toUsageMetricsQuery(startTime, endTime, tenantId, withTenants)
        .fold(RestErrorMapper::mapProblemToResponse, this::getMetrics);
  }

  @CamundaGetMapping(path = "/configuration")
  public ResponseEntity<SystemConfigurationResponse> getSystemConfiguration() {
    final JobMetricsConfiguration jobMetricsCfg = gatewayRestConfiguration.getJobMetrics();
    final var jobMetricsResponse =
        new JobMetricsConfigurationResponse()
            .enabled(jobMetricsCfg.isEnabled())
            .exportInterval(jobMetricsCfg.getExportInterval().toString())
            .maxWorkerNameLength(jobMetricsCfg.getMaxWorkerNameLength())
            .maxJobTypeLength(jobMetricsCfg.getMaxJobTypeLength())
            .maxTenantIdLength(jobMetricsCfg.getMaxTenantIdLength())
            .maxUniqueKeys(jobMetricsCfg.getMaxUniqueKeys());
    final var response = new SystemConfigurationResponse().jobMetrics(jobMetricsResponse);
    return ResponseEntity.ok(response);
  }

  private ResponseEntity<UsageMetricsResponse> getMetrics(final UsageMetricsQuery query) {
    try {
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toUsageMetricsResponse(
              usageMetricsServices.search(query, authenticationProvider.getCamundaAuthentication()),
              query.filter().withTenants()));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
