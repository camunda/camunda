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
import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex.DECISION_REQUIREMENTS_ID;
import static io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex.KEY;
import static io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex.NAME;
import static io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex.VERSION;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.DecisionRequirementsFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

public final class DecisionRequirementsFilterTransformer
    extends IndexFilterTransformer<DecisionRequirementsFilter> {

  public DecisionRequirementsFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final DecisionRequirementsFilter filter) {
    final var keysQuery = getKeysQuery(filter.decisionRequirementsKeys());
    final var namesQuery = getNamesQuery(filter.names());
    final var versionsQuery = getVersionsQuery(filter.versions());
    final var decisionRequirementsIdsQuery =
        getDecisionRequirementsIdsQuery(filter.decisionRequirementsIds());
    final var tenantIdsQuery = getTenantIdsQuery(filter.tenantIds());

    return and(keysQuery, namesQuery, versionsQuery, decisionRequirementsIdsQuery, tenantIdsQuery);
  }

  private SearchQuery getKeysQuery(final List<Long> keys) {
    return longTerms(KEY, keys);
  }

  private SearchQuery getNamesQuery(final List<String> names) {
    return stringTerms(NAME, names);
  }

  private SearchQuery getVersionsQuery(final List<Integer> versions) {
    return intTerms(VERSION, versions);
  }

  private SearchQuery getDecisionRequirementsIdsQuery(final List<String> decisionRequirementsIds) {
    return stringTerms(DECISION_REQUIREMENTS_ID, decisionRequirementsIds);
  }

  private SearchQuery getTenantIdsQuery(final List<String> tenantIds) {
    return stringTerms(TENANT_ID, tenantIds);
  }
}
