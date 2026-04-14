/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.matchAll;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.index.ResourceIndex.RESOURCE_ID;
import static io.camunda.webapps.schema.descriptors.index.ResourceIndex.RESOURCE_KEY;
import static io.camunda.webapps.schema.descriptors.index.ResourceIndex.TENANT_ID;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.ResourceFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.Optional;

public class ResourceFilterTransformer extends IndexFilterTransformer<ResourceFilter> {

  public ResourceFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final ResourceFilter filter) {
    final var tenantFilter =
        Optional.ofNullable(filter.tenantId()).map(t -> term(TENANT_ID, t)).orElse(null);
    return and(
        longTerms(RESOURCE_KEY, filter.resourceKeys()),
        stringTerms(RESOURCE_ID, filter.resourceIds()),
        tenantFilter);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization<?> authorization) {
    return matchAll();
  }
}
