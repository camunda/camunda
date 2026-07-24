/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.security;

import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.security.impl.SearchAuthorizationScopeRepository;

/**
 * Builds the {@link AuthorizationChecker} for a single physical tenant from that tenant's own
 * {@link SearchClientReaders}. Shared by every per-tenant checker-construction site (currently
 * {@link PhysicalTenantAuthorizationCheckerConfiguration} and {@code
 * io.camunda.application.commons.search.ResourceAccessControllerConfiguration}) so the wrapping
 * logic lives in exactly one place, since the two consumers are gated by different Spring
 * conditions and can't simply share a bean.
 */
public final class AuthorizationCheckerFactory {

  private AuthorizationCheckerFactory() {}

  public static AuthorizationChecker forPhysicalTenant(
      final SearchClientReaders searchClientReaders) {
    return new AuthorizationChecker(
        new SearchAuthorizationScopeRepository(searchClientReaders.authorizationReader()));
  }
}
