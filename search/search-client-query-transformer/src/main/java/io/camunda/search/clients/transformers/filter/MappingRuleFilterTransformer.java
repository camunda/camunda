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
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.index.MappingRuleIndex.CLAIM_NAME;
import static io.camunda.webapps.schema.descriptors.index.MappingRuleIndex.CLAIM_VALUE;
import static io.camunda.webapps.schema.descriptors.index.MappingRuleIndex.MAPPING_RULE_ID;
import static io.camunda.webapps.schema.descriptors.index.MappingRuleIndex.NAME;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.MappingRuleFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public class MappingRuleFilterTransformer extends IndexFilterTransformer<MappingRuleFilter> {

  public MappingRuleFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final MappingRuleFilter filter) {
    return and(
        stringTerms(CLAIM_NAME, filter.claimNames()),
        filter.claimName() == null ? null : term(CLAIM_NAME, filter.claimName()),
        filter.claimValue() == null ? null : term(CLAIM_VALUE, filter.claimValue()),
        filter.name() == null ? null : term(NAME, filter.name()),
        filter.mappingRuleId() == null ? null : term(MAPPING_RULE_ID, filter.mappingRuleId()),
        filter.claims() == null
            ? null
            : or(
                filter.claims().stream()
                    .map(
                        claim ->
                            and(term(CLAIM_NAME, claim.name()), term(CLAIM_VALUE, claim.value())))
                    .toList()),
        filter.mappingRuleIds() == null
            ? null
            : filter.mappingRuleIds().isEmpty()
                ? matchNone()
                : stringTerms(MAPPING_RULE_ID, filter.mappingRuleIds().stream().sorted().toList()));
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(MAPPING_RULE_ID, authorization.resourceIds());
  }
}
