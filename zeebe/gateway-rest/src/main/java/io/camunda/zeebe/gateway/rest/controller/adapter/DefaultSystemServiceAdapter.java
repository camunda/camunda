/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.adapter;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedJobMetricsConfigurationResponseStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedSystemConfigurationResponseStrictContract;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.UsageMetricsServices;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration.JobMetricsConfiguration;
import io.camunda.zeebe.gateway.rest.controller.generated.SystemServiceAdapter;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class DefaultSystemServiceAdapter implements SystemServiceAdapter {

  private final UsageMetricsServices usageMetricsServices;
  private final GatewayRestConfiguration gatewayRestConfiguration;

  public DefaultSystemServiceAdapter(
      final UsageMetricsServices usageMetricsServices,
      final GatewayRestConfiguration gatewayRestConfiguration) {
    this.usageMetricsServices = usageMetricsServices;
    this.gatewayRestConfiguration = gatewayRestConfiguration;
  }

  @Override
  public ResponseEntity<Object> getUsageMetrics(
      final String startTime,
      final String endTime,
      final String tenantId,
      final Boolean withTenants,
      final CamundaAuthentication authentication) {
    return SearchQueryRequestMapper.toUsageMetricsQuery(
            startTime, endTime, tenantId, Boolean.TRUE.equals(withTenants))
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> {
              try {
                return ResponseEntity.ok(
                    SearchQueryResponseMapper.toUsageMetricsResponse(
                        usageMetricsServices.search(query, authentication),
                        query.filter().withTenants()));
              } catch (final Exception e) {
                return mapErrorToResponse(e);
              }
            });
  }

  @Override
  public ResponseEntity<Object> getSystemConfiguration(final CamundaAuthentication authentication) {
    final JobMetricsConfiguration jobMetricsCfg = gatewayRestConfiguration.getJobMetrics();
    final var jobMetricsResponse =
        new GeneratedJobMetricsConfigurationResponseStrictContract(
            jobMetricsCfg.isEnabled(),
            jobMetricsCfg.getExportInterval().toString(),
            jobMetricsCfg.getMaxWorkerNameLength(),
            jobMetricsCfg.getMaxJobTypeLength(),
            jobMetricsCfg.getMaxTenantIdLength(),
            jobMetricsCfg.getMaxUniqueKeys());
    final var response = new GeneratedSystemConfigurationResponseStrictContract(jobMetricsResponse);
    return ResponseEntity.ok(response);
  }
}
