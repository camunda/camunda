/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.security.auth.Authentication;

public final class AuthenticationTransformer implements FilterTransformer<Authentication> {

  @Override
  public SearchQuery toSearchQuery(final Authentication value) {
    // TODO: intermediate implementation, needs to handle cases where
    // tenancy is enabled but caller is not assigned to any tenant.
    // in the best case, such calls are already "rejected" in the
    // authentication layer.
    final var authenticatedTenantIds = value.authenticatedTenantIds();
    if (authenticatedTenantIds != null && !authenticatedTenantIds.isEmpty()) {
      return stringTerms("tenantId", authenticatedTenantIds);
    }
    return null;
  }
}
