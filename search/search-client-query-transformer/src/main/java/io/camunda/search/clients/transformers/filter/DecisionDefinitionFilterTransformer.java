/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.intTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.DecisionDefinitionFilter;
import java.util.List;

public final class DecisionDefinitionFilterTransformer
    implements FilterTransformer<DecisionDefinitionFilter> {

  @Override
  public SearchQuery toSearchQuery(final DecisionDefinitionFilter filter) {
    final var decisionDefinitionKeysQuery =
        getDecisionDefinitionKeysQuery(filter.decisionDefinitionKeys());
    final var decisionDefinitionIdsQuery =
        getDecisionDefinitionIdsQuery(filter.decisionDefinitionIds());
    final var namesQuery = getNamesQuery(filter.names());
    final var versionsQuery = getVersionsQuery(filter.versions());
    final var decisionRequirementsIdsQuery =
        getDecisionRequirementsIdsQuery(filter.decisionRequirementsIds());
    final var decisionRequirementsKeysQuery =
        getDecisionRequirementsKeysQuery(filter.decisionRequirementsKeys());
    final var tenantIdsQuery = getTenantIdsQuery(filter.tenantIds());

    return and(
        decisionDefinitionKeysQuery,
        decisionDefinitionIdsQuery,
        namesQuery,
        versionsQuery,
        decisionRequirementsIdsQuery,
        decisionRequirementsKeysQuery,
        tenantIdsQuery);
  }

  @Override
  public List<String> toIndices(final DecisionDefinitionFilter filter) {
    return List.of("operate-decision-8.3.0_alias");
  }

  private SearchQuery getDecisionDefinitionKeysQuery(final List<Long> keys) {
    return longTerms("key", keys);
  }

  private SearchQuery getDecisionDefinitionIdsQuery(final List<String> decisionDefinitionIds) {
    return stringTerms("decisionId", decisionDefinitionIds);
  }

  private SearchQuery getNamesQuery(final List<String> names) {
    return stringTerms("name", names);
  }

  private SearchQuery getVersionsQuery(final List<Integer> versions) {
    return intTerms("version", versions);
  }

  private SearchQuery getDecisionRequirementsIdsQuery(final List<String> decisionRequirementsIds) {
    return stringTerms("decisionRequirementsId", decisionRequirementsIds);
  }

  private SearchQuery getDecisionRequirementsKeysQuery(final List<Long> decisionRequirementsKeys) {
    return longTerms("decisionRequirementsKey", decisionRequirementsKeys);
  }

  private SearchQuery getTenantIdsQuery(final List<String> tenantIds) {
    return stringTerms("tenantId", tenantIds);
  }
}
