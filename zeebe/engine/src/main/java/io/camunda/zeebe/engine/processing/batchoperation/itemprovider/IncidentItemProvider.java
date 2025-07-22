/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.itemprovider;

import com.google.common.collect.Lists;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPageBuilders;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.util.FilterUtil;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.util.VisibleForTesting;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class IncidentItemProvider implements ItemProvider {

  private final SearchClientsProxy searchClientsProxy;
  private final ProcessInstanceFilter filter;
  private final ProcessInstanceItemProvider processInstanceItemProvider;
  private final SecurityContext securityContext;
  private final BatchOperationMetrics metrics;

  public IncidentItemProvider(
      final SearchClientsProxy searchClientsProxy,
      final BatchOperationMetrics metrics,
      final ProcessInstanceFilter filter,
      final CamundaAuthentication authentication) {
    this.searchClientsProxy = searchClientsProxy;
    this.metrics = metrics;
    this.filter = filter;
    processInstanceItemProvider =
        new ProcessInstanceItemProvider(searchClientsProxy, metrics, filter, authentication);
    securityContext =
        createSecurityContext(
            authentication, Authorization.of(a -> a.processDefinition().readProcessInstance()));
  }

  @Override
  public ItemPage fetchItemPage(final String cursor, final int pageSize) {
    final var processInstanceItemPage = processInstanceItemProvider.fetchItemPage(cursor, pageSize);
    final var incidentItems =
        getIncidentItemsOfProcessInstanceKeys(
            processInstanceItemPage.items().stream()
                .map(Item::itemKey)
                .collect(Collectors.toList()),
            pageSize);

    return new ItemPage(
        new ArrayList<>(incidentItems),
        processInstanceItemPage.endCursor(),
        processInstanceItemPage.total(),
        processInstanceItemPage.isLastPage());
  }

  @VisibleForTesting
  public ProcessInstanceFilter getFilter() {
    return filter;
  }

  /**
   * Fetches the incident items of the given process instance keys. The items are fetched in batches
   * to avoid hitting the potential IN clause size limit of the database. Especially Oracle has a
   * size limit of 1000 there.
   *
   * @param processInstanceKeys the process instance keys to fetch incidents for
   * @return a set of all found incident items
   */
  private Set<Item> getIncidentItemsOfProcessInstanceKeys(
      final List<Long> processInstanceKeys, final int pageSize) {
    final Set<Item> incidents = new LinkedHashSet<>();

    final List<List<Long>> processInstanceKeysBatches = Lists.partition(processInstanceKeys, 1000);

    for (final List<Long> processInstanceKeysBatch : processInstanceKeysBatches) {
      final var filter =
          new IncidentFilter.Builder()
              .processInstanceKeyOperations(
                  FilterUtil.mapDefaultToOperation(processInstanceKeysBatch))
              .build();
      incidents.addAll(fetchIncidentItems(filter, pageSize));
    }

    return incidents;
  }

  private Set<Item> fetchIncidentItems(final IncidentFilter filter, final int pageSize) {
    final var items = new LinkedHashSet<Item>();

    String endCursor = null;
    ItemPage result = null;
    while (result == null || !result.isLastPage()) {
      try {
        result = fetchIncidentPage(filter, endCursor, pageSize);
        metrics.recordQueryAgainstSecondaryDatabase();
      } catch (final Exception e) {
        metrics.recordFailedQueryAgainstSecondaryDatabase();
        throw e;
      }

      items.addAll(result.items());
      endCursor = result.endCursor();
    }

    return items;
  }

  private ItemPage fetchIncidentPage(
      final IncidentFilter incidentFilter, final String endCursor, final int pageSize) {
    final var page = SearchQueryPageBuilders.page().size(pageSize).after(endCursor).build();
    final var query =
        SearchQueryBuilders.incidentSearchQuery().filter(incidentFilter).page(page).build();

    final var result =
        searchClientsProxy.withSecurityContext(securityContext).searchIncidents(query);

    return new ItemPage(
        result.items().stream()
            .map(pi -> new Item(pi.incidentKey(), pi.processInstanceKey()))
            .collect(Collectors.toList()),
        result.endCursor(),
        result.total(),
        result.items().isEmpty() || result.total() < pageSize);
  }
}
