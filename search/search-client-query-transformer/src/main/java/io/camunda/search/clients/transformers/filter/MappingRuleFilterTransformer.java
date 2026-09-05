/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchNone;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.index.MappingRuleIndex.CLAIM_NAME;
import static io.camunda.webapps.schema.descriptors.index.MappingRuleIndex.CLAIM_VALUE;
import static io.camunda.webapps.schema.descriptors.index.MappingRuleIndex.MAPPING_RULE_ID;
import static io.camunda.webapps.schema.descriptors.index.MappingRuleIndex.NAME;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.MappingRuleFilter;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;

public class MappingRuleFilterTransformer extends IndexFilterTransformer<MappingRuleFilter> {

  public MappingRuleFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final MappingRuleFilter filter) {
    final var queries = new ArrayList<>(toSearchQueryFields(filter));

    if (filter.orFilters() != null && !filter.orFilters().isEmpty()) {
      queries.add(or(filter.orFilters().stream().map(f -> and(toSearchQueryFields(f))).toList()));
    }

    return and(queries);
  }

  private ArrayList<SearchQuery> toSearchQueryFields(final MappingRuleFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    queries.add(stringTerms(CLAIM_NAME, filter.claimNames()));
    if (filter.claimName() != null) {
      queries.add(term(CLAIM_NAME, filter.claimName()));
    }
    if (filter.claimValue() != null) {
      queries.add(term(CLAIM_VALUE, filter.claimValue()));
    }
    if (filter.nameOperations() != null && !filter.nameOperations().isEmpty()) {
      queries.addAll(stringOperations(NAME, filter.nameOperations()));
    }
    if (filter.mappingRuleIdOperations() != null && !filter.mappingRuleIdOperations().isEmpty()) {
      queries.addAll(stringOperations(MAPPING_RULE_ID, filter.mappingRuleIdOperations()));
    }
    if (filter.claims() != null) {
      queries.add(
          or(
              filter.claims().stream()
                  .map(
                      claim ->
                          and(term(CLAIM_NAME, claim.name()), term(CLAIM_VALUE, claim.value())))
                  .toList()));
    }
    if (filter.mappingRuleIds() != null) {
      queries.add(
          filter.mappingRuleIds().isEmpty()
              ? matchNone()
              : stringTerms(MAPPING_RULE_ID, filter.mappingRuleIds().stream().sorted().toList()));
    }
    return queries;
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(
      final RequiredAuthorization<?> authorization) {
    return stringTerms(MAPPING_RULE_ID, authorization.resourceIds());
  }
}
