/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.security.api.model.CamundaAuthentication;
import java.util.List;
import java.util.Objects;

/** Adapts {@link CamundaAuthentication} to the engine's {@link AuthorizedTenants} interface. */
public final class AuthorizedTenantsAdapter implements AuthorizedTenants {

  private final CamundaAuthentication auth;

  public AuthorizedTenantsAdapter(final CamundaAuthentication auth) {
    this.auth = auth;
  }

  @Override
  public boolean isAuthorizedForTenantId(final String tenantId) {
    return auth.anonymousUser() || authenticatedTenantIds().contains(tenantId);
  }

  @Override
  public boolean isAuthorizedForTenantIds(final List<String> tenantIds) {
    return auth.anonymousUser() || authenticatedTenantIds().containsAll(tenantIds);
  }

  @Override
  public List<String> getAuthorizedTenantIds() {
    if (auth.anonymousUser()) {
      throw new UnsupportedOperationException(
          "Retrieval of authorized tenants is not supported when authenticated anonymously");
    }
    return authenticatedTenantIds();
  }

  /**
   * The {@link CamundaAuthentication} produced by the claims converter always carries a non-null
   * tenant list, but the record's canonical constructor permits {@code null}; guard defensively so
   * a bare authentication cannot trip an NPE, matching the pre-migration {@code
   * AuthenticatedAuthorizedTenants} behavior.
   */
  private List<String> authenticatedTenantIds() {
    return Objects.requireNonNullElse(auth.authenticatedTenantIds(), List.of());
  }

  @Override
  public boolean isAnonymous() {
    return auth.anonymousUser();
  }
}
