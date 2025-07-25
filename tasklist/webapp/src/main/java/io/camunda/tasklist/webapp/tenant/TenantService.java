/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.tenant;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.reader.TenantAccess;
import io.camunda.security.reader.TenantAccessProvider;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

@Component
public class TenantService {

  private final CamundaAuthenticationProvider authenticationProvider;
  private final TenantAccessProvider tenantAccessProvider;
  private final SecurityConfiguration securityConfiguration;

  public TenantService(
      final CamundaAuthenticationProvider authenticationProvider,
      final TenantAccessProvider tenantAccessProvider,
      final SecurityConfiguration securityConfiguration) {
    this.authenticationProvider = authenticationProvider;
    this.tenantAccessProvider = tenantAccessProvider;
    this.securityConfiguration = securityConfiguration;
  }

  public TenantAccess getAuthenticatedTenants() {
    return withAuthenticationOrDefaultResult(
        tenantAccessProvider::resolveTenantAccess, TenantAccess.wildcard(null));
  }

  public boolean isTenantValid(final String tenantId) {
    return withAuthenticationOrDefaultResult(
        authentication ->
            tenantAccessProvider.hasTenantAccessByTenantId(authentication, tenantId).allowed(),
        true);
  }

  private <T> T withAuthenticationOrDefaultResult(
      final Function<CamundaAuthentication, T> supplier, final T defaultValue) {
    if (hasNoneRequestContext() || !isMultiTenancyEnabled()) {
      return defaultValue;
    }

    final var currentAuthentication = authenticationProvider.getCamundaAuthentication();
    return supplier.apply(currentAuthentication);
  }

  public boolean isMultiTenancyEnabled() {
    return securityConfiguration.getMultiTenancy().isChecksEnabled();
  }

  private boolean hasNoneRequestContext() {
    return RequestContextHolder.getRequestAttributes() == null;
  }
}
