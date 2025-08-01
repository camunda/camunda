/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.UsageMetricsSearchClient;
import io.camunda.search.entities.UsageMetricStatisticsEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;

public final class UsageMetricsServices
    extends SearchQueryService<
        UsageMetricsServices, UsageMetricsQuery, UsageMetricStatisticsEntity> {

  private final UsageMetricsSearchClient usageMetricsSearchClient;

  public UsageMetricsServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final UsageMetricsSearchClient usageMetricsSearchClient,
      final CamundaAuthentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.usageMetricsSearchClient = usageMetricsSearchClient;
  }

  @Override
  public SearchQueryResult<UsageMetricStatisticsEntity> search(final UsageMetricsQuery query) {
    if (query == null) {
      throw new IllegalArgumentException("Query must not be null");
    }
    validateStartAndEndTime(query);
    return SearchQueryResult.of(
        usageMetricsSearchClient
            .withSecurityContext(
                securityContextProvider.provideSecurityContext(
                    authentication, Authorization.of(a -> a.usageMetric().read())))
            .usageMetricStatistics(query));
  }

  private void validateStartAndEndTime(final UsageMetricsQuery query) {
    final var filter = query.filter();
    if (filter.startTime() == null || filter.endTime() == null) {
      throw new IllegalArgumentException("Query must have a start AND end time");
    }
    final var startTime = filter.startTime();
    final var endTime = filter.endTime();
    if (endTime.isBefore(startTime)) {
      throw new IllegalArgumentException("End time must be after start time");
    }
    if (startTime.isAfter(endTime)) {
      throw new IllegalArgumentException("Start time must be before end time");
    }
  }

  @Override
  public UsageMetricsServices withAuthentication(final CamundaAuthentication authentication) {
    return new UsageMetricsServices(
        brokerClient, securityContextProvider, usageMetricsSearchClient, authentication);
  }
}
