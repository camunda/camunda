/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.List;

/** Provides access to authorized tenants. */
public interface AuthorizedTenants {

  AuthorizedTenants DEFAULT_TENANTS =
      new AuthenticatedAuthorizedTenants(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  AuthorizedTenants ANONYMOUS = new AnonymouslyAuthorizedTenants();

  /** Returns true, if the user is authorized to access the passed tenantId. */
  boolean isAuthorizedForTenantId(final String tenantId);

  /** Returns true, if the user is authorized to access all passed tenants. */
  boolean isAuthorizedForTenantIds(final List<String> tenants);

  /** Returns the list of authorized tenants. */
  List<String> getAuthorizedTenantIds();

  /** Returns true if this represents anonymous (unauthenticated) access. */
  boolean isAnonymous();
}
