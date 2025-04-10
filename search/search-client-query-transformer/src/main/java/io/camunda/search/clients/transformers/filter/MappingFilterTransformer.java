/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.index.MappingIndex.CLAIM_NAME;
import static io.camunda.webapps.schema.descriptors.index.MappingIndex.CLAIM_VALUE;
import static io.camunda.webapps.schema.descriptors.index.MappingIndex.NAME;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.MappingFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

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
        filter.claims() == null
            ? null
            : or(
                filter.claims().stream()
                    .map(
                        claim ->
                            and(term(CLAIM_NAME, claim.name()), term(CLAIM_VALUE, claim.value())))
                    .toList()));
  }
}
