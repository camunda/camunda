/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.auth.impl;

import io.camunda.zeebe.auth.api.TenantAuthorizationChecker;
import java.util.List;

public class TenantAuthorizationCheckerImpl implements TenantAuthorizationChecker {

  private final List<String> authorizedTenants;

  public TenantAuthorizationCheckerImpl(final List<String> authorizedTenants) {
    this.authorizedTenants = authorizedTenants;
  }

  @Override
  public Boolean isAuthorized(final String tenantId) {
    return authorizedTenants.contains(tenantId);
  }

  @Override
  public Boolean isFullyAuthorized(final List<String> tenantIds) {
    return authorizedTenants.containsAll(tenantIds);
  }

  public static TenantAuthorizationChecker fromJwtDecoder(final JwtAuthorizationDecoder decoder) {
    final List<String> authorizedTenants =
        decoder.decode().get(JwtAuthorizationDecoder.AUTHORIZED_TENANTS_CLAIM).asList(String.class);
    return new TenantAuthorizationCheckerImpl(authorizedTenants);
  }
}
