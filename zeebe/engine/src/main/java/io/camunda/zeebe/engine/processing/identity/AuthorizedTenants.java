/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class AuthorizedTenants {

  public static final AuthorizedTenants DEFAULT_TENANTS =
      new AuthorizedTenants(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public static final AuthorizedTenants ANONYMOUS =
      new AuthorizedTenants(List.of()) {
        @Override
        public boolean isAuthorizedForTenant(final String tenantId) {
          return true;
        }

        @Override
        public boolean isAuthorizedForTenants(final List<String> tenants) {
          return true;
        }
      };

  private final List<String> authorizedTenants;

  public AuthorizedTenants(final String authorizedTenant) {
    this(List.of(authorizedTenant));
  }

  public AuthorizedTenants(final List<String> authorizedTenants) {
    this.authorizedTenants = authorizedTenants;
  }

  public boolean isAuthorizedForTenant(final String tenantId) {
    return authorizedTenants.contains(tenantId);
  }

  public boolean isAuthorizedForTenants(final List<String> tenants) {
    return new HashSet<>(authorizedTenants).containsAll(tenants);
  }

  public List<String> getAuthorizedTenants() {
    return new ArrayList<>(authorizedTenants);
  }
}
