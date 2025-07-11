/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchNone;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.index.MappingIndex.CLAIM_NAME;
import static io.camunda.webapps.schema.descriptors.index.MappingIndex.CLAIM_VALUE;
import static io.camunda.webapps.schema.descriptors.index.MappingIndex.MAPPING_ID;
import static io.camunda.webapps.schema.descriptors.index.MappingIndex.NAME;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.MappingFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.List;

public class MappingFilterTransformer extends IndexFilterTransformer<MappingFilter> {

  public MappingFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final MappingFilter filter) {
    return and(
        stringTerms(CLAIM_NAME, filter.claimNames()),
        filter.claimName() == null ? null : term(CLAIM_NAME, filter.claimName()),
        filter.claimValue() == null ? null : term(CLAIM_VALUE, filter.claimValue()),
        filter.name() == null ? null : term(NAME, filter.name()),
        filter.mappingId() == null ? null : term(MAPPING_ID, filter.mappingId()),
        filter.claims() == null
            ? null
            : or(
                filter.claims().stream()
                    .map(
                        claim ->
                            and(term(CLAIM_NAME, claim.name()), term(CLAIM_VALUE, claim.value())))
                    .toList()),
        filter.mappingIds() == null
            ? null
            : filter.mappingIds().isEmpty()
                ? matchNone()
                : stringTerms(MAPPING_ID, filter.mappingIds().stream().sorted().toList()));
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return stringTerms(MAPPING_ID, authorization.resourceIds());
  }

  @Override
  protected SearchQuery toTenantCheckSearchQuery(final List<String> tenantIds) {
    return matchAll();
  }
}
