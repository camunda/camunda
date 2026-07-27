/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.impl;

import io.camunda.search.clients.reader.SearchClientReaders;
import io.camunda.security.core.authz.AuthorizationChecker;

/**
 * Builds the {@link AuthorizationChecker} for a single physical tenant from that tenant's own
 * {@link SearchClientReaders}, wrapping the tenant's {@link SearchAuthorizationScopeRepository}.
 *
 * <p>Kept as a shared static factory so every per-tenant checker-construction site in the
 * application wiring layer builds the checker the same way, since those consumers are gated by
 * different Spring conditions and can't simply share a bean.
 */
public final class AuthorizationCheckerFactory {

  private AuthorizationCheckerFactory() {}

  public static AuthorizationChecker forPhysicalTenant(
      final SearchClientReaders searchClientReaders) {
    return new AuthorizationChecker(
        new SearchAuthorizationScopeRepository(searchClientReaders.authorizationReader()));
  }
}
