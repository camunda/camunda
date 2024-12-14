/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class AuthenticatedAuthorizedTenants implements AuthorizedTenants {

  private final List<String> authorizedTenantIds;

  public AuthenticatedAuthorizedTenants(final String authorizedTenantId) {
    this(List.of(authorizedTenantId));
  }

  public AuthenticatedAuthorizedTenants(final List<String> authorizedTenantIds) {
    this.authorizedTenantIds = Objects.requireNonNullElse(authorizedTenantIds, List.of());
  }

  @Override
  public boolean isAuthorizedForTenantId(final String tenantId) {
    return authorizedTenantIds.contains(tenantId);
  }

  @Override
  public boolean isAuthorizedForTenantIds(final List<String> tenantIds) {
    return new HashSet<>(authorizedTenantIds).containsAll(tenantIds);
  }

  @Override
  public List<String> getAuthorizedTenantIds() {
    return new ArrayList<>(authorizedTenantIds);
  }
}
