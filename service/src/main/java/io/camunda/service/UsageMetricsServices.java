/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.UsageMetricsQueryMapper.mapToUsageMetricsTUQuery;

import io.camunda.search.clients.UsageMetricsSearchClient;
import io.camunda.search.entities.UsageMetricStatisticsEntity;
import io.camunda.search.entities.UsageMetricTUStatisticsEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.concurrent.CompletableFuture;

public final class UsageMetricsServices
    extends SearchQueryService<
        UsageMetricsServices,
        UsageMetricsQuery,
        Tuple<UsageMetricStatisticsEntity, UsageMetricTUStatisticsEntity>> {

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
  public SearchQueryResult<Tuple<UsageMetricStatisticsEntity, UsageMetricTUStatisticsEntity>>
      search(final UsageMetricsQuery query) {
    if (query == null) {
      throw new IllegalArgumentException("Query must not be null");
    }
    final UsageMetricsSearchClient authUsageMetricsSearchClient =
        usageMetricsSearchClient.withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.system().readUsageMetric())));

    final CompletableFuture<UsageMetricStatisticsEntity> statsFuture =
        CompletableFuture.supplyAsync(
            () -> authUsageMetricsSearchClient.usageMetricStatistics(query));
    final CompletableFuture<UsageMetricTUStatisticsEntity> tuStatsFuture =
        CompletableFuture.supplyAsync(
            () ->
                authUsageMetricsSearchClient.usageMetricTUStatistics(
                    mapToUsageMetricsTUQuery(query)));

    return SearchQueryResult.of(Tuple.of(statsFuture.join(), tuStatsFuture.join()));
  }

  @Override
  public UsageMetricsServices withAuthentication(final CamundaAuthentication authentication) {
    return new UsageMetricsServices(
        brokerClient, securityContextProvider, usageMetricsSearchClient, authentication);
  }
}
