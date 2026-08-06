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
 * <p>Used by {@code AuthorizationCheckerProviderConfiguration} to build the raw {@link
 * AuthorizationChecker} that {@code CamundaServicesConfiguration} hands to services ({@code
 * DocumentServices}, {@code SecretServices}) that query it directly, bypassing the {@code
 * ResourceAccessController}/data-plane path. {@code ResourceAccessControllerConfiguration} no
 * longer uses this factory — its {@code ResourceAccessProvider}s are built from an {@link
 * io.camunda.security.core.port.out.AuthorizationScopeRepositoryPort} instead, via {@code
 * DefaultResourceAccessProvider.forScopeRepository(...)}.
 */
public final class AuthorizationCheckerFactory {

  private AuthorizationCheckerFactory() {}

  public static AuthorizationChecker forPhysicalTenant(
      final SearchClientReaders searchClientReaders) {
    return new AuthorizationChecker(
        new SearchAuthorizationScopeRepository(searchClientReaders.authorizationReader()));
  }
}
