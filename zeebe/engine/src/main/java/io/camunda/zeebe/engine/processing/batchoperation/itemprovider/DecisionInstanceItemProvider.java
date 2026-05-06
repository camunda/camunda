/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.itemprovider;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.page.SearchQueryPageBuilders;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.stream.Collectors;

public class DecisionInstanceItemProvider implements ItemProvider {

  private final SearchClientsProxy searchClientsProxy;
  private final BatchOperationMetrics metrics;
  private final SecurityContext securityContext;
  private final DecisionInstanceFilter filter;

  public DecisionInstanceItemProvider(
      final SearchClientsProxy searchClientsProxy,
      final BatchOperationMetrics metrics,
      final DecisionInstanceFilter filter,
      final CamundaAuthentication authentication) {
    this.searchClientsProxy = searchClientsProxy;
    this.metrics = metrics;
    this.filter = filter;
    securityContext =
        createSecurityContext(
            authentication, Authorization.of(a -> a.decisionDefinition().readDecisionInstance()));
  }

  @Override
  public ItemPage fetchItemPage(final String cursor, final int pageSize) {
    final var page = SearchQueryPageBuilders.page().size(pageSize).after(cursor).build();
    final var query =
        SearchQueryBuilders.decisionInstanceSearchQuery().filter(filter).page(page).build();

    final SearchQueryResult<DecisionInstanceEntity> result;

    try {
      metrics.recordQueryAgainstSecondaryDatabase();
      result =
          searchClientsProxy.withSecurityContext(securityContext).searchDecisionInstances(query);
    } catch (final Exception e) {
      metrics.recordFailedQueryAgainstSecondaryDatabase();
      throw e;
    }

    final boolean isLastPage = result.items().isEmpty() || result.total() < pageSize;

    return new ItemPage(
        result.items().stream()
            .map(
                di ->
                    new Item(
                        di.decisionInstanceKey(),
                        di.processInstanceKey(),
                        di.rootProcessInstanceKey()))
            .collect(Collectors.toList()),
        result.endCursor(),
        result.total(),
        isLastPage);
  }

  @VisibleForTesting
  public DecisionInstanceFilter getFilter() {
    return filter;
  }
}
