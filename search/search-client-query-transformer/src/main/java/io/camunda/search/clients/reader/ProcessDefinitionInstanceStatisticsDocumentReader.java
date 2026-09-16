/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_FIELD_KEY;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_FIELD_PROCESS_DEFINITION_ID;
import static io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation.AGGREGATION_TERMS_SIZE;

import io.camunda.search.aggregation.result.ProcessDefinitionInstanceStatisticsAggregationResult;
import io.camunda.search.clients.SearchClientBasedQueryExecutor;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessDefinitionEntity.ProcessDefinitionState;
import io.camunda.search.entities.ProcessDefinitionInstanceStatisticsEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.reader.ResourceAccessChecks;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.entities.ProcessEntity;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ProcessDefinitionInstanceStatisticsDocumentReader extends DocumentBasedReader
    implements ProcessDefinitionInstanceStatisticsReader {

  public ProcessDefinitionInstanceStatisticsDocumentReader(
      final SearchClientBasedQueryExecutor executor, final IndexDescriptor indexDescriptor) {
    super(executor, indexDescriptor);
  }

  @Override
  public SearchQueryResult<ProcessDefinitionInstanceStatisticsEntity> aggregate(
      final ProcessDefinitionInstanceStatisticsQuery query,
      final ResourceAccessChecks resourceAccessChecks) {

    // Convert sorting field if needed
    final var updatedQuery =
        query.withConvertedSortingField(
            AGGREGATION_FIELD_PROCESS_DEFINITION_ID, AGGREGATION_FIELD_KEY);

    // Run a single paginated query; total count is provided by the cardinality aggregation
    final var paginatedResult =
        getSearchExecutor()
            .aggregateWithQueryResult(
                updatedQuery,
                ProcessDefinitionInstanceStatisticsAggregationResult.class,
                resourceAccessChecks,
                ProcessDefinitionInstanceStatisticsAggregationResult::items);

    final var items =
        withCorrectedHasMultipleVersions(paginatedResult.items(), resourceAccessChecks);

    // Return paginated items and total from the single query result
    return new SearchQueryResult<>(
        paginatedResult.total(),
        paginatedResult.hasMoreTotalItems(),
        items,
        paginatedResult.startCursor(),
        paginatedResult.endCursor());
  }

  /**
   * The primary aggregation above computes {@code hasMultipleVersions} from active process
   * instances (an ES/OS aggregation query can only target one index), so a deployed version with
   * zero active instances is invisible to it — see #51617. Recompute it from the process
   * definitions themselves, which is the actual source of truth for "how many versions are
   * deployed."
   */
  private List<ProcessDefinitionInstanceStatisticsEntity> withCorrectedHasMultipleVersions(
      final List<ProcessDefinitionInstanceStatisticsEntity> items,
      final ResourceAccessChecks resourceAccessChecks) {
    if (items.isEmpty()) {
      return items;
    }

    final var processDefinitionIds =
        items.stream().map(i -> i.processDefinitionId()).distinct().toList();
    final var tenantIds = items.stream().map(i -> i.tenantId()).distinct().toList();
    final var requestedPairs =
        items.stream()
            .map(i -> Tuple.of(i.processDefinitionId(), i.tenantId()))
            .collect(Collectors.toSet());

    final Map<Tuple<String, String>, Set<Integer>> versionsByProcessAndTenant =
        fetchDeployedVersionsByProcessAndTenant(
            processDefinitionIds, tenantIds, requestedPairs, resourceAccessChecks);

    return items.stream()
        .map(
            item -> {
              final var versions =
                  versionsByProcessAndTenant.get(
                      Tuple.of(item.processDefinitionId(), item.tenantId()));
              // An empty lookup means the process-definition query found nothing for this
              // pair (e.g. an access-check mismatch) rather than "only one version exists" —
              // keep the primary aggregation's value in that case instead of forcing false.
              final boolean hasMultipleVersions =
                  versions == null || versions.isEmpty()
                      ? item.hasMultipleVersions()
                      : versions.size() > 1;
              if (hasMultipleVersions == item.hasMultipleVersions()) {
                return item;
              }
              return new ProcessDefinitionInstanceStatisticsEntity(
                  item.processDefinitionId(),
                  item.tenantId(),
                  item.latestProcessDefinitionName(),
                  hasMultipleVersions,
                  item.activeInstancesWithoutIncidentCount(),
                  item.activeInstancesWithIncidentCount());
            })
        .toList();
  }

  /**
   * The process-definition lookup above is bounded to {@link
   * io.camunda.search.aggregation.ProcessDefinitionInstanceStatisticsAggregation#AGGREGATION_TERMS_SIZE}
   * documents per page (an ES/OS terms query can only target one index and does not support an
   * unlimited scroll here), so a deployed version living beyond the first page was previously
   * invisible — see #51617. Walk every page with a stable cursor until the lookup is exhausted,
   * keeping at most two distinct versions per requested pair since that is all {@code
   * hasMultipleVersions} needs to decide.
   */
  private Map<Tuple<String, String>, Set<Integer>> fetchDeployedVersionsByProcessAndTenant(
      final List<String> processDefinitionIds,
      final List<String> tenantIds,
      final Set<Tuple<String, String>> requestedPairs,
      final ResourceAccessChecks resourceAccessChecks) {
    final Map<Tuple<String, String>, Set<Integer>> versionsByProcessAndTenant = new HashMap<>();
    String cursor = null;
    boolean hasMorePages = true;

    while (hasMorePages) {
      final var afterCursor = cursor;
      final var deployedVersionsQuery =
          ProcessDefinitionQuery.of(
              q ->
                  q.filter(
                          f ->
                              f.processDefinitionIdOperations(
                                      List.of(Operation.in(processDefinitionIds)))
                                  .tenantIds(tenantIds))
                      .sort(s -> s.processDefinitionKey().asc())
                      .page(
                          SearchQueryPage.of(
                              p -> p.size(AGGREGATION_TERMS_SIZE).after(afterCursor))));

      final SearchQueryResult<ProcessDefinitionEntity> page =
          getSearchExecutor()
              .search(deployedVersionsQuery, ProcessEntity.class, resourceAccessChecks);

      for (final var pd : page.items()) {
        if (pd.state() == ProcessDefinitionState.DELETED) {
          continue;
        }
        final var pair = Tuple.of(pd.processDefinitionId(), pd.tenantId());
        if (!requestedPairs.contains(pair)) {
          continue;
        }
        final var versions = versionsByProcessAndTenant.computeIfAbsent(pair, k -> new HashSet<>());
        if (versions.size() < 2) {
          versions.add(pd.version());
        }
      }

      hasMorePages = !page.items().isEmpty() && page.items().size() >= AGGREGATION_TERMS_SIZE;
      cursor = page.endCursor();
    }

    return versionsByProcessAndTenant;
  }
}
