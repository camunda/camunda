/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.system;

import static io.camunda.zeebe.gateway.rest.RestErrorMapper.mapErrorToResponse;

import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.UsageMetricsServices;
import io.camunda.zeebe.gateway.protocol.rest.UsageMetricsResponse;
import io.camunda.zeebe.gateway.rest.RestErrorMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryRequestMapper;
import io.camunda.zeebe.gateway.rest.SearchQueryResponseMapper;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequiresSecondaryStorage
@RequestMapping("/v2/system/usage-metrics")
public class UsageMetricsController {

  private final UsageMetricsServices usageMetricsServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public UsageMetricsController(
      final UsageMetricsServices usageMetricsServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.usageMetricsServices = usageMetricsServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaGetMapping
  public ResponseEntity<UsageMetricsResponse> getUsageMetrics(
      @RequestParam(required = false) final String startTime,
      @RequestParam(required = false) final String endTime,
      @RequestParam(required = false) final String tenantId,
      @RequestParam(required = false, defaultValue = "false") final boolean withTenants) {

    return SearchQueryRequestMapper.toUsageMetricsQuery(startTime, endTime, tenantId, withTenants)
        .fold(RestErrorMapper::mapProblemToResponse, this::getMetrics);
  }

  private ResponseEntity<UsageMetricsResponse> getMetrics(final UsageMetricsQuery query) {
    try {
      final var result =
          usageMetricsServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toUsageMetricsResponse(result, query.filter().withTenants()));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
