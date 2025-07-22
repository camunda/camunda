/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.itemprovider;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPageBuilders;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.stream.Collectors;

public class ProcessInstanceItemProvider implements ItemProvider {

  private final SearchClientsProxy searchClientsProxy;
  private final BatchOperationMetrics metrics;
  private final SecurityContext securityContext;
  private final ProcessInstanceFilter filter;

  public ProcessInstanceItemProvider(
      final SearchClientsProxy searchClientsProxy,
      final BatchOperationMetrics metrics,
      final ProcessInstanceFilter filter,
      final CamundaAuthentication authentication) {
    this.searchClientsProxy = searchClientsProxy;
    this.metrics = metrics;
    this.filter = filter;
    securityContext =
        createSecurityContext(
            authentication, Authorization.of(a -> a.processDefinition().readProcessInstance()));
  }

  @Override
  public ItemPage fetchItemPage(final String cursor, final int pageSize) {
    final var page = SearchQueryPageBuilders.page().size(pageSize).after(cursor).build();
    final var query =
        SearchQueryBuilders.processInstanceSearchQuery()
            .filter(filter)
            .page(page)
            .resultConfig(c -> c.onlyKey(true))
            .build();

    final SearchQueryResult<ProcessInstanceEntity> result;

    try {
      metrics.recordQueryAgainstSecondaryDatabase();
      result =
          searchClientsProxy.withSecurityContext(securityContext).searchProcessInstances(query);
    } catch (final Exception e) {
      metrics.recordFailedQueryAgainstSecondaryDatabase();
      throw e;
    }

    final boolean isLastPage = result.items().isEmpty() || result.total() < pageSize;

    return new ItemPage(
        result.items().stream()
            .map(pi -> new Item(pi.processInstanceKey(), pi.processInstanceKey()))
            .collect(Collectors.toList()),
        result.endCursor(),
        result.total(),
        isLastPage);
  }

  @VisibleForTesting
  public ProcessInstanceFilter getFilter() {
    return filter;
  }
}
