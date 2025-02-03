/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.AuthorizationIndex.OWNER_ID;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.AuthorizationIndex.OWNER_TYPE;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.AuthorizationIndex.PERMISSIONS;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.AuthorizationIndex.PERMISSIONS_RESOURCEIDS;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.AuthorizationIndex.PERMISSIONS_TYPE;
import static io.camunda.webapps.schema.descriptors.usermanagement.index.AuthorizationIndex.RESOURCE_TYPE;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.AuthorizationFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;

public final class AuthorizationFilterTransformer
    extends IndexFilterTransformer<AuthorizationFilter> {

  public AuthorizationFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final AuthorizationFilter filter) {
    return and(
        stringTerms(OWNER_ID, filter.ownerIds()),
        filter.ownerType() == null ? null : term(OWNER_TYPE, filter.ownerType()),
        stringTerms("%s.%s".formatted(PERMISSIONS, PERMISSIONS_RESOURCEIDS), filter.resourceIds()),
        filter.resourceType() == null ? null : term(RESOURCE_TYPE, filter.resourceType()),
        filter.permissionType() == null
            ? null
            : term(
                "%s.%s".formatted(PERMISSIONS, PERMISSIONS_TYPE), filter.permissionType().name()));
  }
}
