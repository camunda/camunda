/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.intTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.search.filter.DecisionDefinitionFilter;
import java.util.List;

public final class DecisionDefinitionFilterTransformer
    implements FilterTransformer<DecisionDefinitionFilter> {

  @Override
  public SearchQuery toSearchQuery(final DecisionDefinitionFilter filter) {
    final var idsQuery = getIdsQuery(filter.ids());
    final var keysQuery = getKeysQuery(filter.keys());
    final var decisionIdsQuery = getDecisionIdsQuery(filter.decisionIds());
    final var namesQuery = getNamesQuery(filter.names());
    final var versionsQuery = getVersionsQuery(filter.versions());
    final var decisionRequirementsIdsQuery =
        getDecisionRequirementsIdsQuery(filter.decisionRequirementsIds());
    final var decisionRequirementsKeysQuery =
        getDecisionRequirementsKeysQuery(filter.decisionRequirementsKeys());
    final var decisionRequirementsNames =
        getDecisionRequirementsNames(filter.decisionRequirementsNames());
    final var decisionRequirementsVersions =
        getDecisionRequirementsVersions(filter.decisionRequirementsVersions());
    final var tenantIdsQuery = getTenantIdsQuery(filter.tenantIds());

    return and(
        idsQuery,
        keysQuery,
        decisionIdsQuery,
        namesQuery,
        versionsQuery,
        decisionRequirementsIdsQuery,
        decisionRequirementsKeysQuery,
        decisionRequirementsNames,
        decisionRequirementsVersions,
        tenantIdsQuery);
  }

  private SearchQuery getIdsQuery(final List<String> ids) {
    return stringTerms("id", ids);
  }

  private SearchQuery getKeysQuery(final List<Long> keys) {
    return longTerms("key", keys);
  }

  private SearchQuery getDecisionIdsQuery(final List<String> decisionIds) {
    return stringTerms("decisionId", decisionIds);
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

  private SearchQuery getDecisionRequirementsNames(final List<String> decisionRequirementsNames) {
    return stringTerms("decisionRequirementsName", decisionRequirementsNames);
  }

  private SearchQuery getDecisionRequirementsVersions(
      final List<Integer> decisionRequirementsVersions) {
    return intTerms("decisionRequirementsVersion", decisionRequirementsVersions);
  }

  private SearchQuery getTenantIdsQuery(final List<String> tenantIds) {
    return stringTerms("tenantId", tenantIds);
  }

  @Override
  public List<String> toIndices(DecisionDefinitionFilter filter) {
    return List.of("operate-decision-8.3.0_alias");
  }
}
