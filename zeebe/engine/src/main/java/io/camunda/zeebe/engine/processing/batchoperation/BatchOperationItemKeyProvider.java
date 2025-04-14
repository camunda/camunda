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
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.FilterBase;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.page.SearchQueryPageBuilders;
import io.camunda.search.query.SearchQueryBuilders;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides methods to fetch entity keys for batch operations. It uses the search client
 * proxy to access the secondary database.
 */
public class BatchOperationItemKeyProvider {

  private static final Logger LOG = LoggerFactory.getLogger(BatchOperationItemKeyProvider.class);

  /**
   * The size of a page when fetching entity keys. This is the maximum number of keys that can be
   * fetched in a single query and is limited by ElasticSearch.
   */
  private static final int PAGE_SIZE = 10000;

  /**
   * The maximum number of keys that can be passed in a single IN clause in a query. This is limited
   * to 1000 because the Oracle DB supports a maximum of 1000 elements in an IN clause.
   */
  private static final int IN_CLAUSE_SIZE = 1000;

  private final SearchClientsProxy searchClientsProxy;

  public BatchOperationItemKeyProvider(final SearchClientsProxy searchClientsProxy) {
    this.searchClientsProxy = searchClientsProxy;
  }

  /**
   * Fetches the process instance keys based on the provided filter. The keys are fetched
   * sequentially in pages. Depending on the overall size of the result this can cause multiple
   * queries to the secondary database.
   *
   * @param filter the filter to use
   * @param shouldAbort if the process should be aborted
   * @return a set of all found process instance keys
   */
  public Set<Long> fetchProcessInstanceKeys(
      final ProcessInstanceFilter filter, final Supplier<Boolean> shouldAbort) {
    return fetchEntityKeys(new ProcessInstanceKeyPageFetcher(), filter, shouldAbort);
  }

  /**
   * Fetches the incident keys based on the provided incident filter. The keys are fetched
   * sequentially in pages. Depending on the overall size of the result this can cause multiple
   * queries to the secondary database.
   *
   * @param filter the filter to use
   * @param shouldAbort if the process should be aborted
   * @return a set of all found process instance keys
   */
  public Set<Long> fetchIncidentKeys(
      final IncidentFilter filter, final Supplier<Boolean> shouldAbort) {
    return fetchEntityKeys(new IncidentKeyPageFetcher(), filter, shouldAbort);
  }

  /**
   * Fetches the incident keys based on the provided process instance filter. This will return
   * <b>ALL</b> incidents of the matching processInstances. The keys are fetched sequentially in
   * pages. Depending on the overall size of the result this can cause multiple queries to the
   * secondary database.
   *
   * @param filter the filter to use
   * @param shouldAbort if the process should be aborted
   * @return a set of all found process instance keys
   */
  public Set<Long> fetchIncidentKeys(
      final ProcessInstanceFilter filter, final Supplier<Boolean> shouldAbort) {
    // first fetch all matching processInstances
    final var processInstanceKeys = fetchProcessInstanceKeys(filter, shouldAbort);

    // then fetch all incidents of the matching processInstances
    return getIncidentKeysOfProcessInstanceKeys(new ArrayList<>(processInstanceKeys), shouldAbort);
  }

  private <F extends FilterBase> Set<Long> fetchEntityKeys(
      final ItemKeyPageFetcher<F> itemKeyPageFetcher,
      final F filter,
      final Supplier<Boolean> shouldAbort) {
    final var itemKeys = new LinkedHashSet<Long>();

    Object[] searchValues = null;
    while (true) {
      // Check if the batch operation is still present, could be canceled in the meantime
      if (shouldAbort.get()) {
        LOG.warn("Batch operation is no longer active, stopping query.");
        return Set.of();
      }

      final var result = itemKeyPageFetcher.fetchKeys(filter, searchValues);
      itemKeys.addAll(result.keys);
      searchValues = result.lastSortValues();

      if (itemKeys.size() >= result.total() || result.keys.isEmpty()) {
        break;
      }
    }

    return itemKeys;
  }

  private Set<Long> getIncidentKeysOfProcessInstanceKeys(
      final List<Long> processInstanceKeys, final Supplier<Boolean> shouldAbort) {
    final Set<Long> incidentKeys = new HashSet<>();

    final List<List<Long>> processInstanceKeysBatches =
        Lists.partition(processInstanceKeys, IN_CLAUSE_SIZE);

    for (final List<Long> processInstanceKeysBatch : processInstanceKeysBatches) {
      if (shouldAbort.get()) {
        return Set.of();
      }
      final var filter =
          new IncidentFilter.Builder().processInstanceKeys(processInstanceKeysBatch).build();
      incidentKeys.addAll(fetchIncidentKeys(filter, shouldAbort));
    }

    return incidentKeys;
  }

  /**
   * Internal abstraction to hold the result of a page of entity keys.
   *
   * @param keys the fetched keys
   * @param lastSortValues the last sortValues for pagination
   * @param total the total amount of found keys
   */
  private record ItemKeyPage(List<Long> keys, Object[] lastSortValues, long total) {}

  /**
   * Internal abstraction interface to get a single page of entity keys of a specific type. This is
   * needed because the actual query to the secondary database is different for specific entity
   * types.
   *
   * @param <F> the filter type
   */
  private interface ItemKeyPageFetcher<F extends FilterBase> {

    /**
     * Fetches a page of entity keys based on the provided filter and search values.
     *
     * @param filter the filter to apply
     * @param sortValues the current sortValues
     * @return the fetched keys and pagination information
     */
    ItemKeyPage fetchKeys(F filter, Object[] sortValues);
  }

  private final class ProcessInstanceKeyPageFetcher
      implements ItemKeyPageFetcher<ProcessInstanceFilter> {
    @Override
    public ItemKeyPage fetchKeys(final ProcessInstanceFilter filter, final Object[] sortValues) {
      final var page =
          SearchQueryPageBuilders.page().size(PAGE_SIZE).searchAfter(sortValues).build();
      final var query =
          SearchQueryBuilders.processInstanceSearchQuery()
              .filter(filter)
              .page(page)
              .resultConfig(c -> c.onlyKey(true))
              .build();
      final var result = searchClientsProxy.searchProcessInstances(query);
      return new ItemKeyPage(
          result.items().stream()
              .map(ProcessInstanceEntity::processInstanceKey)
              .collect(Collectors.toList()),
          result.lastSortValues(),
          result.total());
    }
  }

  private final class IncidentKeyPageFetcher implements ItemKeyPageFetcher<IncidentFilter> {
    @Override
    public ItemKeyPage fetchKeys(final IncidentFilter filter, final Object[] sortValues) {
      final var page =
          SearchQueryPageBuilders.page().size(PAGE_SIZE).searchAfter(sortValues).build();
      final var query = SearchQueryBuilders.incidentSearchQuery().filter(filter).page(page).build();
      final var result = searchClientsProxy.searchIncidents(query);
      return new ItemKeyPage(
          result.items().stream().map(IncidentEntity::incidentKey).collect(Collectors.toList()),
          result.lastSortValues(),
          result.total());
    }
  }
}
