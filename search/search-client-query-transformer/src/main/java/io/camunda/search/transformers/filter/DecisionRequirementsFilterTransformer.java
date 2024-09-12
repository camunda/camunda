/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.intTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.search.filter.DecisionRequirementsFilter;
import java.util.List;

public final class DecisionRequirementsFilterTransformer
    implements FilterTransformer<DecisionRequirementsFilter> {

  @Override
  public SearchQuery toSearchQuery(final DecisionRequirementsFilter filter) {
    final var keysQuery = getKeysQuery(filter.decisionRequirementsKeys());
    final var namesQuery = getNamesQuery(filter.dmnDecisionRequirementsNames());
    final var versionsQuery = getVersionsQuery(filter.versions());
    final var decisionRequirementsIdsQuery =
        getDecisionRequirementsIdsQuery(filter.decisionRequirementsIds());
    final var tenantIdsQuery = getTenantIdsQuery(filter.tenantIds());

    return and(keysQuery, namesQuery, versionsQuery, decisionRequirementsIdsQuery, tenantIdsQuery);
  }

  @Override
  public List<String> toIndices(final DecisionRequirementsFilter filter) {
    return List.of("operate-decision-requirements-8.3.0_alias");
  }

  private SearchQuery getKeysQuery(final List<Long> keys) {
    return longTerms("key", keys);
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

  private SearchQuery getTenantIdsQuery(final List<String> tenantIds) {
    return stringTerms("tenantId", tenantIds);
  }
}
