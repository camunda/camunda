/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import com.google.common.collect.Lists;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPageBuilders;
import io.camunda.search.query.SearchQueryBuilders;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides methods to fetch entities for batch operations. It uses the search client
 * proxy to access the secondary database.
 */
public class BatchOperationItemProvider {

  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationItemProvider.class);

  /**
   * The size of a page when fetching entities. This is the maximum number of items that can be
   * fetched in a single query and is limited by ElasticSearch.
   */
  private static final int PAGE_SIZE = 10000;

  /**
   * The maximum number of keys that can be passed in a single IN clause in a query. This is limited
   * to 1000 because the Oracle DB supports a maximum of 1000 elements in an IN clause.
   */
  private static final int IN_CLAUSE_SIZE = 1000;

  private final SearchClientsProxy searchClientsProxy;

  public BatchOperationItemProvider(final SearchClientsProxy searchClientsProxy) {
    this.searchClientsProxy = searchClientsProxy;
  }

  /**
   * Fetches the process instance items based on the provided filter. The items are fetched
   * sequentially in pages. Depending on the overall size of the result this can cause multiple
   * queries to the secondary database.
   *
   * @param filter the filter to use
   * @param shouldAbort if the process should be aborted
   * @return a set of all found process instance items
   */
  public Set<Item> fetchProcessInstanceItems(
      final ProcessInstanceFilter filter, final Supplier<Boolean> shouldAbort) {
    return fetchEntityItems(new ProcessInstancePageFetcher(), filter, shouldAbort);
  }

  /**
   * Fetches the incident items based on the provided incident filter. The items are fetched
   * sequentially in pages. Depending on the overall size of the result this can cause multiple
   * queries to the secondary database.
   *
   * @param filter the filter to use
   * @param shouldAbort if the process should be aborted
   * @return a set of all found process instance items
   */
  public Set<Item> fetchIncidentItems(
      final IncidentFilter filter, final Supplier<Boolean> shouldAbort) {
    return fetchEntityItems(new IncidentPageFetcher(), filter, shouldAbort);
  }

  /**
   * Fetches the incident items based on the provided process instance filter. This will return
   * <b>ALL</b> incidents of the matching processInstances. The items are fetched sequentially in
   * pages. Depending on the overall size of the result this can cause multiple queries to the
   * secondary database.
   *
   * @param filter the filter to use
   * @param shouldAbort if the process should be aborted
   * @return a set of all found incident items
   */
  public Set<Item> fetchIncidentItems(
      final ProcessInstanceFilter filter, final Supplier<Boolean> shouldAbort) {
    // first fetch all matching processInstances
    final var processInstanceKeys =
        fetchProcessInstanceItems(filter, shouldAbort).stream()
            .map(Item::processInstanceKey)
            .toList();

    // then fetch all incidents of the matching processInstances
    return getIncidentItemsOfProcessInstanceKeys(new ArrayList<>(processInstanceKeys), shouldAbort);
  }

  private <F extends FilterBase> Set<Item> fetchEntityItems(
      final ItemPageFetcher<F> itemPageFetcher,
      final F filter,
      final Supplier<Boolean> shouldAbort) {
    final var items = new LinkedHashSet<Item>();

    Object[] searchValues = null;
    while (true) {
      // Check if the batch operation is still present, could be canceled in the meantime
      if (shouldAbort.get()) {
        LOG.warn("Batch operation is no longer active, stopping query.");
        return Set.of();
      }

      final var result = itemPageFetcher.fetchItems(filter, searchValues);
      items.addAll(result.items);
      searchValues = result.lastSortValues();

      if (items.size() >= result.total() || result.items.isEmpty()) {
        break;
      }
    }

    return items;
  }

  private Set<Item> getIncidentItemsOfProcessInstanceKeys(
      final List<Long> processInstanceKeys, final Supplier<Boolean> shouldAbort) {
    final Set<Item> incidents = new LinkedHashSet<>();

    final List<List<Long>> processInstanceKeysBatches =
        Lists.partition(processInstanceKeys, IN_CLAUSE_SIZE);

    for (final List<Long> processInstanceKeysBatch : processInstanceKeysBatches) {
      if (shouldAbort.get()) {
        return Set.of();
      }
      final var filter =
          new IncidentFilter.Builder().processInstanceKeys(processInstanceKeysBatch).build();
      incidents.addAll(fetchIncidentItems(filter, shouldAbort));
    }

    return incidents;
  }

  public record Item(long itemKey, long processInstanceKey) {}

  /**
   * Internal abstraction to hold the result of a page of entity items.
   *
   * @param items the fetched items
   * @param lastSortValues the last sortValues for pagination
   * @param total the total amount of found items
   */
  private record ItemPage(List<Item> items, Object[] lastSortValues, long total) {}

  /**
   * Internal abstraction interface to get a single page of entity items of a specific type. This is
   * needed because the actual query to the secondary database is different for specific entity
   * types.
   *
   * @param <F> the filter type
   */
  private interface ItemPageFetcher<F extends FilterBase> {

    /**
     * Fetches a page of entity items based on the provided filter and search values.
     *
     * @param filter the filter to apply
     * @param sortValues the current sortValues
     * @return the fetched items and pagination information
     */
    ItemPage fetchItems(F filter, Object[] sortValues);
  }

  private final class ProcessInstancePageFetcher implements ItemPageFetcher<ProcessInstanceFilter> {
    @Override
    public ItemPage fetchItems(final ProcessInstanceFilter filter, final Object[] sortValues) {
      final var page =
          SearchQueryPageBuilders.page().size(PAGE_SIZE).searchAfter(sortValues).build();
      final var query =
          SearchQueryBuilders.processInstanceSearchQuery()
              .filter(filter)
              .page(page)
              .resultConfig(c -> c.onlyKey(true))
              .build();
      final var result = searchClientsProxy.searchProcessInstances(query);
      return new ItemPage(
          result.items().stream()
              .map(pi -> new Item(pi.processInstanceKey(), pi.processInstanceKey()))
              .collect(Collectors.toList()),
          result.lastSortValues(),
          result.total());
    }
  }

  private final class IncidentPageFetcher implements ItemPageFetcher<IncidentFilter> {
    @Override
    public ItemPage fetchItems(final IncidentFilter filter, final Object[] sortValues) {
      final var page =
          SearchQueryPageBuilders.page().size(PAGE_SIZE).searchAfter(sortValues).build();
      final var query = SearchQueryBuilders.incidentSearchQuery().filter(filter).page(page).build();
      final var result = searchClientsProxy.searchIncidents(query);
      return new ItemPage(
          result.items().stream()
              .map(pi -> new Item(pi.incidentKey(), pi.processInstanceKey()))
              .collect(Collectors.toList()),
          result.lastSortValues(),
          result.total());
    }
  }
}
